package com.classhub.notification;

import com.classhub.notification.config.NotificationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeadlineReminderJob {

    private final NotificationProperties properties;
    private final DeadlineReminderScheduler reminderScheduler;

    public DeadlineReminderJob(
            NotificationProperties properties, DeadlineReminderScheduler reminderScheduler) {
        this.properties = properties;
        this.reminderScheduler = reminderScheduler;
    }

    @Scheduled(fixedDelayString = "${classhub.notifications.reminders.scheduler-interval:PT1H}")
    public void run() {
        reminderScheduler.runReminders();
    }
}
