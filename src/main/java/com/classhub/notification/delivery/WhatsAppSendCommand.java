package com.classhub.notification.delivery;

import java.util.UUID;

public record WhatsAppSendCommand(UUID deliveryId, String destination, String text) {
}
