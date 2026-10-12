package com.classhub.notification.delivery;

import com.classhub.auth.ClassRepAccountSetup;
import com.classhub.auth.ClassRepAccountSetupRepository;
import com.classhub.auth.ClassRepSetupTokenFactory;
import com.classhub.auth.PasswordResetChallenge;
import com.classhub.auth.PasswordResetChallengeRepository;
import com.classhub.auth.PasswordResetSecretFactory;
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
    private final ClassRepAccountSetupRepository setupRepository;
    private final ClassRepSetupTokenFactory setupTokenFactory;
    private final PasswordResetChallengeRepository passwordResetRepository;
    private final PasswordResetSecretFactory passwordResetSecretFactory;

    public NotificationMessageResolver(
            NotificationTemplateService templateService,
            ClassRepAccountSetupRepository setupRepository,
            ClassRepSetupTokenFactory setupTokenFactory,
            PasswordResetChallengeRepository passwordResetRepository,
            PasswordResetSecretFactory passwordResetSecretFactory) {
        this.templateService = templateService;
        this.setupRepository = setupRepository;
        this.setupTokenFactory = setupTokenFactory;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordResetSecretFactory = passwordResetSecretFactory;
    }

    public NotificationMessage fromDelivery(NotificationDelivery delivery) {
        Notification notification = delivery.getNotification();
        String actionPath = actionPathFor(notification);
        String body = messageBody(notification);
        Map<String, String> meta = new HashMap<>();
        meta.put("occurrenceKey", notification.getOccurrenceKey());
        return new NotificationMessage(
                notification.getType(),
                notification.getTitle(),
                body,
                body,
                actionLabelFor(notification.getType()),
                actionPath,
                isCoursework(notification.getType()) ? notification.getReferenceId() : null,
                isAnnouncement(notification.getType()) ? notification.getReferenceId() : null,
                null,
                null,
                meta);
    }

    private String messageBody(Notification notification) {
        if (notification.getType() != NotificationType.PASSWORD_RESET_OTP) {
            return notification.getMessage();
        }
        PasswordResetChallenge challenge = passwordResetRepository.findById(notification.getReferenceId())
                .orElseThrow(() -> new IllegalStateException("Password reset challenge not found"));
        String otp = passwordResetSecretFactory.otp(challenge.getId(), challenge.getRequestedAt());
        return "Your ClassHub verification code is " + otp
                + ". It expires in 10 minutes. Do not share this code. "
                + "If you did not request a password reset, ignore this message.";
    }

    private String actionPathFor(Notification notification) {
        if (notification.getReferenceId() == null) {
            return "/";
        }
        if (notification.getType() == NotificationType.ACCOUNT_SETUP) {
            ClassRepAccountSetup setup = setupRepository.findById(notification.getReferenceId())
                    .orElseThrow(() -> new IllegalStateException("Account setup issuance not found"));
            String token = setupTokenFactory.create(setup.getId(), setup.getIssuedAt());
            return "/complete-account?token=" + token;
        }
        if (notification.getType() == NotificationType.PASSWORD_RESET_OTP) {
            return "/forgot-password";
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

    private static String actionLabelFor(NotificationType type) {
        return switch (type) {
            case ACCOUNT_SETUP -> "Complete account setup";
            case PASSWORD_RESET_OTP -> "Reset password";
            case COURSEWORK_PUBLISHED, COURSEWORK_DEADLINE_REMINDER,
                    COURSEWORK_DEADLINE_CHANGED, COURSEWORK_CANCELLED,
                    COURSEWORK_INSTRUCTIONS_UPDATED -> "View coursework";
            case ANNOUNCEMENT_PUBLISHED -> "View announcement";
            default -> "Open ClassHub";
        };
    }
}
