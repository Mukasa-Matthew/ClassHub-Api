package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.config.NotificationProperties;
import org.springframework.stereotype.Component;

@Component
public class SopraSendWhatsAppNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private final NotificationProperties properties;
    private final WhatsAppProvider provider;

    public SopraSendWhatsAppNotificationDeliveryAdapter(
            NotificationProperties properties, WhatsAppProvider provider) {
        this.properties = properties;
        this.provider = provider;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public DeliveryResult send(NotificationDeliveryRequest request) {
        if (!properties.getWhatsapp().isEnabled()) {
            return DeliveryResult.skipped("PROVIDER_DISABLED", "WhatsApp provider is disabled");
        }
        String destination = WhatsAppPhoneNormalizer.toProviderNumber(request.recipientPhone());
        if (destination == null) {
            return DeliveryResult.skipped("INVALID_PHONE", "Recipient phone number is invalid");
        }
        return provider.send(new WhatsAppSendCommand(
                request.deliveryId(), destination, messageText(request)));
    }

    private String messageText(NotificationDeliveryRequest request) {
        NotificationMessage message = request.message();
        String base = properties.getWebBaseUrl().replaceAll("/+$", "");
        String path = message.actionPath() == null || message.actionPath().isBlank()
                ? "/"
                : message.actionPath();
        String url = base + (path.startsWith("/") ? path : "/" + path);
        String summary = message.shortText() == null || message.shortText().isBlank()
                ? message.body()
                : message.shortText();
        return "%s\n\n%s\n\nOpen ClassHub for details: %s"
                .formatted(message.title(), summary, url);
    }
}
