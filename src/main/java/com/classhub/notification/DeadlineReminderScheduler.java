package com.classhub.notification;

import com.classhub.coursework.Coursework;
import com.classhub.coursework.CourseworkRepository;
import com.classhub.coursework.CourseworkStatus;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeadlineReminderScheduler {

    private final NotificationProperties properties;
    private final CourseworkRepository courseworkRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationOrchestrator orchestrator;
    private final NotificationReminderLogRepository reminderLogRepository;
    private final NotificationEligibilityService eligibilityService;
    private final Clock clock;

    public DeadlineReminderScheduler(
            NotificationProperties properties,
            CourseworkRepository courseworkRepository,
            NotificationRecipientResolver recipientResolver,
            NotificationOrchestrator orchestrator,
            NotificationReminderLogRepository reminderLogRepository,
            NotificationEligibilityService eligibilityService,
            Clock clock) {
        this.properties = properties;
        this.courseworkRepository = courseworkRepository;
        this.recipientResolver = recipientResolver;
        this.orchestrator = orchestrator;
        this.reminderLogRepository = reminderLogRepository;
        this.eligibilityService = eligibilityService;
        this.clock = clock;
    }

    @Transactional
    public void runReminders() {
        if (!properties.isEnabled() || !properties.getReminders().isEnabled()) {
            return;
        }
        ZoneId zone = properties.zoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Coursework> published =
                courseworkRepository.findDetailedByStatusOrderByDueAtAsc(CourseworkStatus.PUBLISHED);
        Instant now = clock.instant();

        for (Coursework coursework : published) {
            if (coursework.getDueAt() == null) {
                continue;
            }
            List<User> students =
                    recipientResolver.activeClassMembers(coursework.getCourseUnit().getAcademicClass().getId());
            LocalDate dueDate = coursework.getDueAt().atZone(zone).toLocalDate();
            long daysUntil = ChronoUnit.DAYS.between(today, dueDate);

            ReminderType windowType = windowForDaysUntil(daysUntil);
            if (windowType != null) {
                processReminderWindow(coursework, students, windowType, now);
            }
            if (coursework.getDueAt().isBefore(now)) {
                processOverdue(coursework, students, now);
            }
        }
    }

    private ReminderType windowForDaysUntil(long daysUntil) {
        int days = (int) daysUntil;
        if (!properties.getReminders().getDeadlineReminderDays().contains(days)) {
            return null;
        }
        return switch (days) {
            case 7 -> ReminderType.DAYS_7;
            case 3 -> ReminderType.DAYS_3;
            case 1 -> ReminderType.DAYS_1;
            case 0 -> ReminderType.DEADLINE_DAY;
            default -> null;
        };
    }

    private void processReminderWindow(
            Coursework coursework, List<User> students, ReminderType type, Instant now) {
        for (User student : students) {
            if (!eligibilityService.isReminderEligible(student, coursework.getId())) {
                continue;
            }
            if (reminderLogRepository.existsByStudentIdAndCourseworkIdAndReminderType(
                    student.getId(), coursework.getId(), type)) {
                continue;
            }
            try {
                orchestrator.onCourseworkDeadlineReminder(coursework, student, type);
                reminderLogRepository.saveAndFlush(
                        new NotificationReminderLog(student, coursework, type, now));
            } catch (DataIntegrityViolationException ex) {
                // idempotent duplicate
            }
        }
    }

    private void processOverdue(Coursework coursework, List<User> students, Instant now) {
        ReminderType type = ReminderType.OVERDUE_ONCE;
        for (User student : students) {
            if (!eligibilityService.isReminderEligible(student, coursework.getId())) {
                continue;
            }
            if (reminderLogRepository.existsByStudentIdAndCourseworkIdAndReminderType(
                    student.getId(), coursework.getId(), type)) {
                continue;
            }
            try {
                orchestrator.onCourseworkDeadlineReminder(coursework, student, type);
                reminderLogRepository.saveAndFlush(
                        new NotificationReminderLog(student, coursework, type, now));
            } catch (DataIntegrityViolationException ex) {
                // idempotent duplicate
            }
        }
    }
}
