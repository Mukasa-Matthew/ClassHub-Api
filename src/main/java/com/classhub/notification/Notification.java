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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false, updatable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 64)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "occurrence_key", nullable = false, length = 128)
    private String occurrenceKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            User recipient,
            NotificationType type,
            String title,
            String message,
            UUID referenceId,
            String referenceType,
            String occurrenceKey) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.occurrenceKey = occurrenceKey == null || occurrenceKey.isBlank() ? "default" : occurrenceKey;
        this.read = false;
        this.readAt = null;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getOccurrenceKey() {
        return occurrenceKey;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markRead(Instant now) {
        if (this.read) {
            return;
        }
        this.read = true;
        this.readAt = now;
    }
}
