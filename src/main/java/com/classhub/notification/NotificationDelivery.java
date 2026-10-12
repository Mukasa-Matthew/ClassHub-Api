package com.classhub.notification;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(Notification notification, User user, NotificationChannel channel) {
        this.notification = notification;
        this.user = user;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public User getUser() {
        return user;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void markProcessing(Instant now) {
        this.status = DeliveryStatus.PROCESSING;
        this.lastAttemptAt = now;
        this.attemptCount = attemptCount + 1;
    }

    public void markSent(Instant now, String providerMessageId) {
        this.status = DeliveryStatus.SENT;
        this.sentAt = now;
        this.providerMessageId = providerMessageId;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.nextAttemptAt = null;
    }

    public void markSkipped(String code, String message) {
        this.status = DeliveryStatus.SKIPPED;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.nextAttemptAt = null;
    }

    public void markFailed(Instant now, String code, String message, Instant nextAttempt) {
        this.status = DeliveryStatus.FAILED;
        this.lastAttemptAt = now;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.nextAttemptAt = nextAttempt;
    }

    public void scheduleRetry(Instant nextAttempt, Instant now, String code, String message) {
        this.status = DeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttempt;
        this.lastAttemptAt = now;
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
    }
}
