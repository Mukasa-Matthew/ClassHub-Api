package com.classhub.notification.delivery;

public interface WhatsAppProvider {

    DeliveryResult send(WhatsAppSendCommand command);
}
