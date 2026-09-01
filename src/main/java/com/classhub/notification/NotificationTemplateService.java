package com.classhub.notification;

import com.classhub.announcement.Announcement;
import com.classhub.courseunit.CourseUnit;
import com.classhub.coursework.Coursework;
import com.classhub.coursework.CourseworkType;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.NotificationMessage;
import com.classhub.user.User;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public static final String REFERENCE_COURSEWORK = "COURSEWORK";
    public static final String REFERENCE_ANNOUNCEMENT = "ANNOUNCEMENT";

    private static final int INSTRUCTION_PREVIEW = 200;
    private static final int ANNOUNCEMENT_PREVIEW = 280;

    private final NotificationProperties properties;
    private final DateTimeFormatter deadlineFormatter;

    public NotificationTemplateService(NotificationProperties properties) {
        this.properties = properties;
        this.deadlineFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)
                .withZone(properties.zoneId());
    }

    public NotificationMessage courseworkPublished(Coursework coursework) {
        CourseUnit unit = coursework.getCourseUnit();
        String unitLabel = unitLabel(unit);
        String deadline = formatDeadline(coursework.getDueAt());
        String preview = previewInstructions(coursework.getInstructions());
        String shortText = unitLabel + " • " + coursework.getType() + " • Due " + deadline;
        String body = "Coursework: " + coursework.getTitle()
                + "\nCourse unit: " + unit.getName()
                + "\nType: " + coursework.getType()
                + "\nDue: " + deadline
                + (preview.isEmpty() ? "" : "\nInstructions: " + preview);
        return new NotificationMessage(
                NotificationType.COURSEWORK_PUBLISHED,
                "New coursework: " + coursework.getTitle(),
                shortText,
                body,
                "View coursework",
                "/coursework/" + coursework.getId(),
                coursework.getId(),
                null,
                unit.getId(),
                coursework.getDueAt(),
                Map.of("courseUnitName", unit.getName(), "courseworkType", coursework.getType().name()));
    }

    public NotificationMessage announcementPublished(Announcement announcement) {
        String preview = truncate(announcement.getContent(), ANNOUNCEMENT_PREVIEW);
        String body = announcement.getTitle() + "\n" + preview;
        return new NotificationMessage(
                NotificationType.ANNOUNCEMENT_PUBLISHED,
                "New announcement: " + announcement.getTitle(),
                preview,
                body,
                "View announcement",
                "/announcements/" + announcement.getId(),
                null,
                announcement.getId(),
                null,
                null,
                Map.of("classContext", "ClassHub"));
    }

    public NotificationMessage courseworkDeadlineReminder(
            Coursework coursework, User student, ReminderType reminderType) {
        CourseUnit unit = coursework.getCourseUnit();
        String unitLabel = unitLabel(unit);
        String deadline = formatDeadline(coursework.getDueAt());
        String timeRemaining = timeRemainingLabel(reminderType);
        String shortText = unitLabel + " • " + timeRemaining + " • Due " + deadline;
        String body = "Reminder for " + coursework.getTitle()
                + "\nCourse unit: " + unit.getName()
                + "\n" + timeRemaining
                + "\nDue: " + deadline;
        Map<String, String> meta = new HashMap<>();
        meta.put("studentFirstName", student.getFirstName());
        meta.put("reminderType", reminderType.name());
        return new NotificationMessage(
                NotificationType.COURSEWORK_DEADLINE_REMINDER,
                "Deadline reminder: " + coursework.getTitle(),
                shortText,
                body,
                "View coursework",
                "/coursework/" + coursework.getId(),
                coursework.getId(),
                null,
                unit.getId(),
                coursework.getDueAt(),
                meta);
    }

    public NotificationMessage courseworkDeadlineChanged(
            Coursework coursework, Instant oldDueAt, Instant newDueAt) {
        CourseUnit unit = coursework.getCourseUnit();
        String unitLabel = unitLabel(unit);
        String oldDeadline = formatDeadline(oldDueAt);
        String newDeadline = formatDeadline(newDueAt);
        String shortText = unitLabel + " • Deadline changed to " + newDeadline;
        String body = coursework.getTitle()
                + "\nCourse unit: " + unit.getName()
                + "\nPrevious deadline: " + oldDeadline
                + "\nNew deadline: " + newDeadline;
        Map<String, String> meta = new HashMap<>();
        meta.put("oldDeadline", oldDeadline);
        meta.put("newDeadline", newDeadline);
        return new NotificationMessage(
                NotificationType.COURSEWORK_DEADLINE_CHANGED,
                "Deadline changed: " + coursework.getTitle(),
                shortText,
                body,
                "View coursework",
                "/coursework/" + coursework.getId(),
                coursework.getId(),
                null,
                unit.getId(),
                newDueAt,
                meta);
    }

    public NotificationMessage courseworkCancelled(Coursework coursework) {
        CourseUnit unit = coursework.getCourseUnit();
        String unitLabel = unitLabel(unit);
        String deadline = formatDeadline(coursework.getDueAt());
        String shortText = unitLabel + " • Cancelled";
        String body = coursework.getTitle()
                + "\nCourse unit: " + unit.getName()
                + "\nStatus: CANCELLED"
                + "\nOriginal deadline: " + deadline;
        return new NotificationMessage(
                NotificationType.COURSEWORK_CANCELLED,
                "Coursework cancelled: " + coursework.getTitle(),
                shortText,
                body,
                "View coursework",
                "/coursework/" + coursework.getId(),
                coursework.getId(),
                null,
                unit.getId(),
                coursework.getDueAt(),
                Map.of("status", "CANCELLED"));
    }

    public NotificationMessage courseworkInstructionsUpdated(Coursework coursework) {
        CourseUnit unit = coursework.getCourseUnit();
        String unitLabel = unitLabel(unit);
        String preview = previewInstructions(coursework.getInstructions());
        String shortText = unitLabel + " • Instructions updated";
        String body = coursework.getTitle()
                + "\nCourse unit: " + unit.getName()
                + "\nUpdated instructions: " + preview;
        return new NotificationMessage(
                NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED,
                "Instructions updated: " + coursework.getTitle(),
                shortText,
                body,
                "View coursework",
                "/coursework/" + coursework.getId(),
                coursework.getId(),
                null,
                unit.getId(),
                coursework.getDueAt(),
                Map.of("courseUnitName", unit.getName()));
    }

    public String inAppMessage(NotificationMessage message) {
        return message.body();
    }

    public String actionUrl(String actionPath) {
        String base = properties.getWebBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (actionPath == null || actionPath.isBlank()) {
            return base;
        }
        if (!actionPath.startsWith("/")) {
            return base + "/" + actionPath;
        }
        return base + actionPath;
    }

    private String unitLabel(CourseUnit unit) {
        if (unit.getCode() != null && !unit.getCode().isBlank()) {
            return unit.getCode();
        }
        return unit.getName();
    }

    private String formatDeadline(Instant dueAt) {
        if (dueAt == null) {
            return "—";
        }
        return deadlineFormatter.format(dueAt);
    }

    private static String previewInstructions(String instructions) {
        if (instructions == null || instructions.isBlank()) {
            return "";
        }
        return truncate(instructions.trim(), INSTRUCTION_PREVIEW);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private static String timeRemainingLabel(ReminderType type) {
        switch (type) {
            case DAYS_7:
                return "Due in 7 days";
            case DAYS_3:
                return "Due in 3 days";
            case DAYS_1:
                return "Due in 1 day";
            case DEADLINE_DAY:
                return "Due today";
            case OVERDUE_ONCE:
                return "Overdue";
            default:
                return "Deadline reminder";
        }
    }

    public static String occurrenceKeyForReminder(ReminderType type) {
        return "REMINDER:" + type.name();
    }

    public static String occurrenceKeyForDeadlineChange(Instant oldDue, Instant newDue) {
        return "DEADLINE:" + oldDue.toEpochMilli() + ":" + newDue.toEpochMilli();
    }

    public static String occurrenceKeyDefault() {
        return "default";
    }

    public ZoneId zoneId() {
        return properties.zoneId();
    }
}
