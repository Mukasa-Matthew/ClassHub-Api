package com.classhub.notification.push;

import java.util.UUID;

public record WebPushSendCommand(
        UUID deliveryId,
        String endpoint,
        String p256dh,
        String auth,
        String title,
        String body,
        String actionUrl,
        String notificationType,
        String referenceId) {}
