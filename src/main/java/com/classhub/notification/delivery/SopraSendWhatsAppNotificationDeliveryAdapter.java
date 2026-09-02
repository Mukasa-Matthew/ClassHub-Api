package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.config.NotificationProperties;
import org.springframework.stereotype.Component;

@Component
public class SopraSendWhatsAppNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private final NotificationProperties properties;
    private final WhatsAppProvider provider;
    private final NotificationChannelTemplateService templateService;

    public SopraSendWhatsAppNotificationDeliveryAdapter(
            NotificationProperties properties,
            WhatsAppProvider provider,
            NotificationChannelTemplateService templateService) {
        this.properties = properties;
        this.provider = provider;
        this.templateService = templateService;
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
                request.deliveryId(), destination, templateService.whatsapp(request)));
    }
}
