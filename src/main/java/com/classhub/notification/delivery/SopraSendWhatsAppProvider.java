package com.classhub.notification.delivery;

import com.classhub.notification.config.NotificationProperties;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SopraSendWhatsAppProvider implements WhatsAppProvider {

    private final NotificationProperties properties;
    private final RestClient restClient;

    public SopraSendWhatsAppProvider(
            NotificationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public DeliveryResult send(WhatsAppSendCommand command) {
        NotificationProperties.WhatsApp config = properties.getWhatsapp();
        if (isBlank(config.getApiKey()) || isBlank(config.getDeviceId())) {
            return DeliveryResult.failed(
                    "PROVIDER_NOT_CONFIGURED", "SopraSend credentials are incomplete");
        }

        String endpoint = config.getBaseUrl().replaceAll("/+$", "") + "/api/v1/messages/send";
        try {
            SopraSendResponse response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("accept", "application/json")
                    .body(Map.of(
                            "device_id", config.getDeviceId(),
                            "to", command.destination(),
                            "text", command.text()))
                    .retrieve()
                    .body(SopraSendResponse.class);
            if (response == null || isBlank(response.messageId())) {
                return DeliveryResult.failed(
                        "INVALID_PROVIDER_RESPONSE", "SopraSend returned no message identifier");
            }
            return DeliveryResult.sent(response.messageId());
        } catch (RestClientResponseException ex) {
            return providerFailure(ex.getStatusCode());
        } catch (RestClientException ex) {
            return DeliveryResult.failed("SOPRASEND_UNAVAILABLE", "SopraSend could not be reached");
        }
    }

    private static DeliveryResult providerFailure(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> DeliveryResult.failed("SOPRASEND_AUTHENTICATION_FAILED", "SopraSend authentication failed");
            case 403 -> DeliveryResult.failed("SOPRASEND_FORBIDDEN", "SopraSend rejected the configured operation");
            case 404 -> DeliveryResult.failed("SOPRASEND_DEVICE_NOT_FOUND", "SopraSend device was not found");
            case 422 -> DeliveryResult.failed("SOPRASEND_INVALID_REQUEST", "SopraSend rejected the delivery data");
            case 429 -> DeliveryResult.failed("SOPRASEND_RATE_LIMITED", "SopraSend rate limit reached");
            default -> status.is5xxServerError()
                    ? DeliveryResult.failed("SOPRASEND_UNAVAILABLE", "SopraSend is temporarily unavailable")
                    : DeliveryResult.failed("SOPRASEND_REJECTED", "SopraSend rejected the delivery request");
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SopraSendResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("message_id") String messageId) {
    }
}
