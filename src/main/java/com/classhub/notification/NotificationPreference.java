package com.classhub.notification;

import com.classhub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(User user, boolean emailEnabled, boolean whatsappEnabled) {
        this.user = user;
        this.emailEnabled = emailEnabled;
        this.whatsappEnabled = whatsappEnabled;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isWhatsappEnabled() {
        return whatsappEnabled;
    }

    public void update(boolean emailEnabled, boolean whatsappEnabled) {
        this.emailEnabled = emailEnabled;
        this.whatsappEnabled = whatsappEnabled;
    }
}
