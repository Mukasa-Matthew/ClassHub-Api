package com.classhub.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReminderLogRepository extends JpaRepository<NotificationReminderLog, UUID> {

    boolean existsByStudentIdAndCourseworkIdAndReminderType(
            UUID studentId, UUID courseworkId, ReminderType reminderType);
}
