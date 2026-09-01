package com.classhub.notification;

import com.classhub.announcement.Announcement;
import com.classhub.coursework.Coursework;
import com.classhub.coursework.CourseworkStatus;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.NotificationDeliveryRequest;
import com.classhub.notification.delivery.NotificationMessage;
import com.classhub.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOrchestrator {

    private final NotificationProperties properties;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationEligibilityService eligibilityService;
    private final NotificationTemplateService templateService;

    public NotificationOrchestrator(
            NotificationProperties properties,
            NotificationRepository notificationRepository,
            NotificationDeliveryRepository deliveryRepository,
            NotificationRecipientResolver recipientResolver,
            NotificationEligibilityService eligibilityService,
            NotificationTemplateService templateService) {
        this.properties = properties;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.recipientResolver = recipientResolver;
        this.eligibilityService = eligibilityService;
        this.templateService = templateService;
    }

    @Transactional
    public void onCourseworkPublished(Coursework coursework) {
        if (!properties.isEnabled()) {
            return;
        }
        NotificationMessage message = templateService.courseworkPublished(coursework);
        dispatchToClassMembers(
                message,
                coursework.getCourseUnit().getAcademicClass().getId(),
                coursework.getId(),
                NotificationTemplateService.REFERENCE_COURSEWORK,
                NotificationTemplateService.occurrenceKeyDefault());
    }

    @Transactional
    public void onAnnouncementPublished(Announcement announcement) {
        if (!properties.isEnabled()) {
            return;
        }
        NotificationMessage message = templateService.announcementPublished(announcement);
        dispatchToClassMembers(
                message,
                announcement.getAcademicClass().getId(),
                announcement.getId(),
                NotificationTemplateService.REFERENCE_ANNOUNCEMENT,
                NotificationTemplateService.occurrenceKeyDefault());
    }

    @Transactional
    public void onCourseworkDeadlineChanged(Coursework coursework, Instant oldDueAt, Instant newDueAt) {
        if (!properties.isEnabled() || oldDueAt.equals(newDueAt)) {
            return;
        }
        NotificationMessage message = templateService.courseworkDeadlineChanged(coursework, oldDueAt, newDueAt);
        String occurrenceKey = NotificationTemplateService.occurrenceKeyForDeadlineChange(oldDueAt, newDueAt);
        dispatchToClassMembers(
                message,
                coursework.getCourseUnit().getAcademicClass().getId(),
                coursework.getId(),
                NotificationTemplateService.REFERENCE_COURSEWORK,
                occurrenceKey);
    }

    @Transactional
    public void onCourseworkCancelled(Coursework coursework) {
        if (!properties.isEnabled()) {
            return;
        }
        NotificationMessage message = templateService.courseworkCancelled(coursework);
        dispatchToClassMembers(
                message,
                coursework.getCourseUnit().getAcademicClass().getId(),
                coursework.getId(),
                NotificationTemplateService.REFERENCE_COURSEWORK,
                "CANCELLED");
    }

    @Transactional
    public void onCourseworkInstructionsUpdated(Coursework coursework) {
        if (!properties.isEnabled()) {
            return;
        }
        NotificationMessage message = templateService.courseworkInstructionsUpdated(coursework);
        dispatchToClassMembers(
                message,
                coursework.getCourseUnit().getAcademicClass().getId(),
                coursework.getId(),
                NotificationTemplateService.REFERENCE_COURSEWORK,
                "INSTRUCTIONS:" + coursework.getUpdatedAt().toEpochMilli());
    }

    @Transactional
    public void onCourseworkDeadlineReminder(
            Coursework coursework, User student, ReminderType reminderType) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!eligibilityService.isReminderEligible(student, coursework.getId())) {
            return;
        }
        NotificationMessage message =
                templateService.courseworkDeadlineReminder(coursework, student, reminderType);
        String occurrenceKey = NotificationTemplateService.occurrenceKeyForReminder(reminderType);
        dispatchToStudent(
                student,
                message,
                coursework.getId(),
                NotificationTemplateService.REFERENCE_COURSEWORK,
                occurrenceKey);
    }

    private void dispatchToClassMembers(
            NotificationMessage message,
            UUID classId,
            UUID referenceId,
            String referenceType,
            String occurrenceKey) {
        for (User student : recipientResolver.activeClassMembers(classId)) {
            dispatchToStudent(student, message, referenceId, referenceType, occurrenceKey);
        }
    }

    private void dispatchToStudent(
            User student,
            NotificationMessage message,
            UUID referenceId,
            String referenceType,
            String occurrenceKey) {
        if (!eligibilityService.isChannelEligible(student, NotificationChannel.IN_APP)) {
            return;
        }
        try {
            Notification notification = new Notification(
                    student,
                    message.eventType(),
                    message.title(),
                    templateService.inAppMessage(message),
                    referenceId,
                    referenceType,
                    occurrenceKey);
            notificationRepository.saveAndFlush(notification);
            createDeliveries(student, notification, message);
        } catch (DataIntegrityViolationException ex) {
            // duplicate occurrence — idempotent no-op
        }
    }

    private void createDeliveries(User student, Notification notification, NotificationMessage message) {
        List<NotificationDelivery> deliveries = new ArrayList<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            String skipReason = eligibilityService.eligibilityReason(student, channel);
            NotificationDelivery delivery = new NotificationDelivery(notification, student, channel);
            if (skipReason != null) {
                delivery.markSkipped(skipReason, safeSkipMessage(skipReason));
            }
            deliveries.add(delivery);
        }
        deliveryRepository.saveAll(deliveries);
        deliveryRepository.flush();
    }

    public NotificationDeliveryRequest toDeliveryRequest(
            NotificationDelivery delivery, NotificationMessage message) {
        User user = delivery.getUser();
        return new NotificationDeliveryRequest(
                delivery.getId(),
                delivery.getNotification().getId(),
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFirstName(),
                delivery.getChannel(),
                message.eventType(),
                message);
    }

    private static String safeSkipMessage(String code) {
        return switch (code) {
            case NotificationEligibilityService.SKIP_PROVIDER_DISABLED -> "Channel provider not configured";
            case NotificationEligibilityService.SKIP_PREFERENCE_DISABLED -> "User preference disabled";
            case NotificationEligibilityService.SKIP_NO_CONTACT -> "No contact available";
            default -> "Not eligible";
        };
    }
}
