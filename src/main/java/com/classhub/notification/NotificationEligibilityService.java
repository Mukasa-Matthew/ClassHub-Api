package com.classhub.notification;

import com.classhub.coursework.CourseworkProgress;
import com.classhub.coursework.CourseworkProgressRepository;
import com.classhub.coursework.CourseworkProgressStatus;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationEligibilityService {

    public static final String SKIP_PROVIDER_DISABLED = "PROVIDER_DISABLED";
    public static final String SKIP_PREFERENCE_DISABLED = "PREFERENCE_DISABLED";
    public static final String SKIP_NO_CONTACT = "NO_CONTACT";
    public static final String SKIP_NOT_STUDENT = "NOT_STUDENT";

    private final NotificationProperties properties;
    private final NotificationPreferenceRepository preferenceRepository;
    private final CourseworkProgressRepository progressRepository;

    public NotificationEligibilityService(
            NotificationProperties properties,
            NotificationPreferenceRepository preferenceRepository,
            CourseworkProgressRepository progressRepository) {
        this.properties = properties;
        this.preferenceRepository = preferenceRepository;
        this.progressRepository = progressRepository;
    }

    public boolean isChannelEligible(User user, NotificationChannel channel) {
        return eligibilityReason(user, channel) == null;
    }

    public boolean isChannelEligible(User user, NotificationChannel channel, NotificationType eventType) {
        return eligibilityReason(user, channel, eventType) == null;
    }

    public String eligibilityReason(User user, NotificationChannel channel) {
        return eligibilityReason(user, channel, null);
    }

    public String eligibilityReason(User user, NotificationChannel channel, NotificationType eventType) {
        if (!user.getRole().isStudentLike() || user.getStatus() != UserStatus.ACTIVE) {
            return SKIP_NOT_STUDENT;
        }
        if (channel == NotificationChannel.IN_APP) {
            return null;
        }
        NotificationPreference prefs = preferenceRepository
                .findByUserId(user.getId())
                .orElseGet(() -> defaultPreference(user));
        if (channel == NotificationChannel.EMAIL) {
            if (!properties.getEmail().isEnabled()) {
                return SKIP_PROVIDER_DISABLED;
            }
            if (respectsPreferences(eventType) && !prefs.isEmailEnabled()) {
                return SKIP_PREFERENCE_DISABLED;
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                return SKIP_NO_CONTACT;
            }
            return null;
        }
        if (channel == NotificationChannel.WHATSAPP) {
            if (!properties.getWhatsapp().isEnabled()) {
                return SKIP_PROVIDER_DISABLED;
            }
            if (respectsPreferences(eventType) && !prefs.isWhatsappEnabled()) {
                return SKIP_PREFERENCE_DISABLED;
            }
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                return SKIP_NO_CONTACT;
            }
            return null;
        }
        return SKIP_PROVIDER_DISABLED;
    }

    private static boolean respectsPreferences(NotificationType eventType) {
        return eventType == null || eventType.respectsAcademicChannelPreferences();
    }

    public boolean isReminderEligible(User student, UUID courseworkId) {
        return !isProgressCompleted(courseworkId, student.getId());
    }

    public boolean isProgressCompleted(UUID courseworkId, UUID studentId) {
        return progressRepository
                .findByCourseworkIdAndStudentId(courseworkId, studentId)
                .map(CourseworkProgress::getProgressStatus)
                .orElse(CourseworkProgressStatus.NOT_STARTED) == CourseworkProgressStatus.COMPLETED;
    }

    private NotificationPreference defaultPreference(User user) {
        return new NotificationPreference(user, true, false);
    }
}
