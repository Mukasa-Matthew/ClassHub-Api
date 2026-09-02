package com.classhub.notification.push;

import com.classhub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "endpoint", nullable = false, length = 2048)
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64, updatable = false)
    private String endpointHash;

    @Column(name = "p256dh_key", nullable = false, length = 180)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 64)
    private String authKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PushSubscription() {}

    public PushSubscription(
            User user, String endpoint, String endpointHash, String p256dhKey, String authKey) {
        this.user = user;
        this.endpoint = endpoint;
        this.endpointHash = endpointHash;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
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

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getEndpointHash() { return endpointHash; }
    public String getP256dhKey() { return p256dhKey; }
    public String getAuthKey() { return authKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateKeys(String p256dhKey, String authKey) {
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
    }
}
