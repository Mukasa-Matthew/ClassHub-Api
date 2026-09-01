package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.config.NotificationProperties;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MetaWhatsAppNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private final NotificationProperties properties;
    private final RestClient restClient;

    public MetaWhatsAppNotificationDeliveryAdapter(
            NotificationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public DeliveryResult send(NotificationDeliveryRequest request) {
        NotificationProperties.WhatsApp config = properties.getWhatsapp();
        if (!config.isEnabled()) {
            return DeliveryResult.skipped("PROVIDER_DISABLED", "WhatsApp provider is disabled");
        }
        if (isBlank(config.getAccessToken()) || isBlank(config.getPhoneNumberId())
                || isBlank(config.getTemplateName())) {
            return DeliveryResult.failed("PROVIDER_NOT_CONFIGURED", "WhatsApp credentials are incomplete");
        }
        String recipient = normalizePhone(request.recipientPhone());
        if (recipient == null) {
            return DeliveryResult.skipped("INVALID_PHONE", "Recipient phone number is invalid");
        }

        String endpoint = "%s/%s/%s/messages".formatted(
                config.getGraphApiBaseUrl().replaceAll("/+$", ""),
                config.getGraphApiVersion(),
                config.getPhoneNumberId());
        try {
            WhatsAppResponse response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + config.getAccessToken())
                    .header("accept", "application/json")
                    .body(payload(request, recipient, config))
                    .retrieve()
                    .body(WhatsAppResponse.class);
            if (response == null || response.messages() == null || response.messages().isEmpty()
                    || isBlank(response.messages().getFirst().id())) {
                return DeliveryResult.failed(
                        "INVALID_PROVIDER_RESPONSE", "WhatsApp returned no message identifier");
            }
            return DeliveryResult.sent(response.messages().getFirst().id());
        } catch (RestClientResponseException ex) {
            return providerFailure(ex.getStatusCode());
        } catch (RestClientException ex) {
            return DeliveryResult.failed("WHATSAPP_UNAVAILABLE", "WhatsApp could not be reached");
        }
    }

    private Map<String, Object> payload(
            NotificationDeliveryRequest request,
            String recipient,
            NotificationProperties.WhatsApp config) {
        List<Map<String, Object>> parameters = List.of(
                textParameter(recipientName(request)),
                textParameter(request.message().title()),
                textParameter(request.message().body()),
                textParameter(actionUrl(request.message().actionPath())));
        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipient,
                "type", "template",
                "template", Map.of(
                        "name", config.getTemplateName(),
                        "language", Map.of("code", config.getTemplateLanguage()),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", parameters))));
    }

    private String actionUrl(String actionPath) {
        String base = properties.getWebBaseUrl().replaceAll("/+$", "");
        String path = isBlank(actionPath) ? "/" : actionPath;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private static Map<String, Object> textParameter(String text) {
        return Map.of("type", "text", "text", text == null ? "" : text);
    }

    private static String recipientName(NotificationDeliveryRequest request) {
        return isBlank(request.recipientFirstName()) ? "Student" : request.recipientFirstName();
    }

    static String normalizePhone(String phone) {
        if (isBlank(phone)) {
            return null;
        }
        String normalized = phone.replaceAll("[^0-9]", "");
        return normalized.length() >= 8 && normalized.length() <= 15 ? normalized : null;
    }

    private static DeliveryResult providerFailure(HttpStatusCode status) {
        if (status.value() == 429) {
            return DeliveryResult.failed("WHATSAPP_RATE_LIMITED", "WhatsApp rate limit reached");
        }
        if (status.is5xxServerError()) {
            return DeliveryResult.failed("WHATSAPP_UNAVAILABLE", "WhatsApp is temporarily unavailable");
        }
        return DeliveryResult.failed("WHATSAPP_REJECTED", "WhatsApp rejected the delivery request");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record WhatsAppResponse(List<WhatsAppMessage> messages) {}
    private record WhatsAppMessage(String id) {}
}
