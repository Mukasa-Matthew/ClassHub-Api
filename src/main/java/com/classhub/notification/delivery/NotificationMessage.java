package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationMessage(
        NotificationType eventType,
        String title,
        String shortText,
        String body,
        String actionLabel,
        String actionPath,
        UUID courseworkId,
        UUID announcementId,
        UUID courseUnitId,
        Instant deadline,
        Map<String, String> metadata) {

    public NotificationMessage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
