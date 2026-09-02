package com.classhub.notification.push;

import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.DeliveryResult;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class VapidWebPushNotificationProvider implements WebPushNotificationProvider {

    public static final String EXPIRED_SUBSCRIPTION = "WEB_PUSH_SUBSCRIPTION_EXPIRED";

    private final NotificationProperties properties;
    private final WebPushTransport transport;
    private final ObjectMapper objectMapper;

    public VapidWebPushNotificationProvider(
            NotificationProperties properties, WebPushTransport transport, ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeliveryResult send(WebPushSendCommand command) {
        NotificationProperties.Push config = properties.getPush();
        if (!config.isEnabled()) {
            return DeliveryResult.skipped("PROVIDER_DISABLED", "Web Push provider is disabled");
        }
        if (!config.isConfigured()) {
            return DeliveryResult.failed(
                    "PROVIDER_NOT_CONFIGURED", "Web Push VAPID configuration is incomplete");
        }
        try {
            String payload = objectMapper.writeValueAsString(new WebPushPayload(
                    command.title(),
                    command.body(),
                    command.actionUrl(),
                    command.notificationType(),
                    command.referenceId()));
            WebPushTransportResponse response = transport.send(new WebPushTransportRequest(
                    command.endpoint(), command.p256dh(), command.auth(), payload,
                    config.getVapidPublicKey(), config.getVapidPrivateKey(), config.getSubject()));
            return mapResponse(response);
        } catch (JacksonException ex) {
            return DeliveryResult.failed("PAYLOAD_ENCODING_ERROR", "Web Push payload could not be encoded");
        } catch (WebPushTransportException ex) {
            return DeliveryResult.failed("WEB_PUSH_UNAVAILABLE", "Web Push service could not be reached");
        }
    }

    private static DeliveryResult mapResponse(WebPushTransportResponse response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return DeliveryResult.sent(response.providerMessageId());
        }
        if (status == 404 || status == 410) {
            return DeliveryResult.failed(EXPIRED_SUBSCRIPTION, "Web Push subscription has expired");
        }
        if (status == 429) {
            return DeliveryResult.failed("WEB_PUSH_RATE_LIMITED", "Web Push service rate limit reached");
        }
        if (status >= 500) {
            return DeliveryResult.failed("WEB_PUSH_UNAVAILABLE", "Web Push service is temporarily unavailable");
        }
        return DeliveryResult.failed("WEB_PUSH_REJECTED", "Web Push service rejected the notification");
    }

    private record WebPushPayload(
            String title, String body, String url, String notificationType, String referenceId) {}
}
