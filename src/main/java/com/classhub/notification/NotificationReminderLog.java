package com.classhub.notification;

import com.classhub.coursework.Coursework;
import com.classhub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_reminder_log")
public class NotificationReminderLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false, updatable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coursework_id", nullable = false, updatable = false)
    private Coursework coursework;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 32)
    private ReminderType reminderType;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected NotificationReminderLog() {
    }

    public NotificationReminderLog(User student, Coursework coursework, ReminderType reminderType, Instant sentAt) {
        this.student = student;
        this.coursework = coursework;
        this.reminderType = reminderType;
        this.sentAt = sentAt;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public Coursework getCoursework() {
        return coursework;
    }

    public ReminderType getReminderType() {
        return reminderType;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
