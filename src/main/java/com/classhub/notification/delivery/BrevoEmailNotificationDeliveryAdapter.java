package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.config.NotificationProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BrevoEmailNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private final NotificationProperties properties;
    private final RestClient restClient;
    private final NotificationChannelTemplateService templateService;

    public BrevoEmailNotificationDeliveryAdapter(
            NotificationProperties properties,
            RestClient.Builder restClientBuilder,
            NotificationChannelTemplateService templateService) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.templateService = templateService;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public DeliveryResult send(NotificationDeliveryRequest request) {
        NotificationProperties.Email config = properties.getEmail();
        if (!config.isEnabled()) {
            return DeliveryResult.skipped("PROVIDER_DISABLED", "Email provider is disabled");
        }
        if (isBlank(config.getApiKey()) || isBlank(config.getSenderEmail())) {
            return DeliveryResult.failed("PROVIDER_NOT_CONFIGURED", "Brevo credentials are incomplete");
        }
        if (isBlank(request.recipientEmail())) {
            return DeliveryResult.skipped("NO_CONTACT", "Recipient email is missing");
        }

        try {
            BrevoResponse response = restClient.post()
                    .uri(config.getApiUrl())
                    .header("api-key", config.getApiKey())
                    .header("accept", "application/json")
                    .body(payload(request, config))
                    .retrieve()
                    .body(BrevoResponse.class);
            if (response == null || isBlank(response.messageId())) {
                return DeliveryResult.failed("INVALID_PROVIDER_RESPONSE", "Brevo returned no message identifier");
            }
            return DeliveryResult.sent(response.messageId());
        } catch (RestClientResponseException ex) {
            return providerFailure("BREVO", ex.getStatusCode());
        } catch (RestClientException ex) {
            return DeliveryResult.failed("BREVO_UNAVAILABLE", "Brevo could not be reached");
        }
    }

    private Map<String, Object> payload(
            NotificationDeliveryRequest request, NotificationProperties.Email config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        NotificationChannelTemplateService.EmailContent content = templateService.email(request);
        payload.put("sender", Map.of("name", config.getSenderName(), "email", config.getSenderEmail()));
        payload.put("to", List.of(Map.of(
                "email", request.recipientEmail(),
                "name", recipientName(request))));
        payload.put("subject", content.subject());
        payload.put("htmlContent", content.html());
        payload.put("textContent", content.plainText());
        payload.put("headers", Map.of("Idempotency-Key", request.deliveryId().toString()));
        if (!isBlank(config.getReplyToEmail())) {
            payload.put("replyTo", Map.of("email", config.getReplyToEmail()));
        }
        if (config.isSandbox()) {
            payload.put("headers", Map.of(
                    "Idempotency-Key", request.deliveryId().toString(),
                    "X-Sib-Sandbox", "drop"));
        }
        return payload;
    }

    private static String recipientName(NotificationDeliveryRequest request) {
        return isBlank(request.recipientFirstName()) ? "ClassHub student" : request.recipientFirstName();
    }

    private static DeliveryResult providerFailure(String provider, HttpStatusCode status) {
        if (status.value() == 429) {
            return DeliveryResult.failed(provider + "_RATE_LIMITED", provider + " rate limit reached");
        }
        if (status.is5xxServerError()) {
            return DeliveryResult.failed(provider + "_UNAVAILABLE", provider + " is temporarily unavailable");
        }
        return DeliveryResult.failed(provider + "_REJECTED", provider + " rejected the delivery request");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BrevoResponse(String messageId) {}
}
