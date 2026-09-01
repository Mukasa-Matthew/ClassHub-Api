package com.classhub.notification.delivery;

import com.classhub.notification.Notification;
import com.classhub.notification.NotificationDelivery;
import com.classhub.notification.NotificationTemplateService;
import com.classhub.notification.NotificationType;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationMessageResolver {

    private final NotificationTemplateService templateService;

    public NotificationMessageResolver(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    public NotificationMessage fromDelivery(NotificationDelivery delivery) {
        Notification notification = delivery.getNotification();
        String actionPath = actionPathFor(notification);
        Map<String, String> meta = new HashMap<>();
        meta.put("occurrenceKey", notification.getOccurrenceKey());
        return new NotificationMessage(
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getMessage(),
                "Open in ClassHub",
                actionPath,
                isCoursework(notification.getType()) ? notification.getReferenceId() : null,
                isAnnouncement(notification.getType()) ? notification.getReferenceId() : null,
                null,
                null,
                meta);
    }

    private String actionPathFor(Notification notification) {
        if (notification.getReferenceId() == null) {
            return "/";
        }
        if (isCoursework(notification.getType())) {
            return "/coursework/" + notification.getReferenceId();
        }
        if (isAnnouncement(notification.getType())) {
            return "/announcements/" + notification.getReferenceId();
        }
        return "/";
    }

    private static boolean isCoursework(NotificationType type) {
        return type == NotificationType.COURSEWORK_PUBLISHED
                || type == NotificationType.COURSEWORK_DEADLINE_REMINDER
                || type == NotificationType.COURSEWORK_DEADLINE_CHANGED
                || type == NotificationType.COURSEWORK_CANCELLED
                || type == NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED;
    }

    private static boolean isAnnouncement(NotificationType type) {
        return type == NotificationType.ANNOUNCEMENT_PUBLISHED;
    }
}
