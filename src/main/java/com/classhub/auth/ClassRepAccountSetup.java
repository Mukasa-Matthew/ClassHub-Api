package com.classhub.auth;

import com.classhub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class_rep_account_setups")
public class ClassRepAccountSetup {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClassRepAccountSetup() {
    }

    public ClassRepAccountSetup(UUID id, User user, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.createdAt = issuedAt;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getSupersededAt() { return supersededAt; }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && supersededAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) { this.usedAt = now; }
    public void supersede(Instant now) { this.supersededAt = now; }
}
