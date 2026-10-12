package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationType;
import java.util.UUID;

public record NotificationDeliveryRequest(
        UUID deliveryId,
        UUID notificationId,
        UUID recipientUserId,
        String recipientEmail,
        String recipientPhone,
        String recipientFirstName,
        NotificationChannel channel,
        NotificationType eventType,
        NotificationMessage message) {
}
