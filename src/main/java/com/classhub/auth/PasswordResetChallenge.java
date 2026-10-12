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
@Table(name = "password_reset_challenges")
public class PasswordResetChallenge {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 64, updatable = false)
    private String otpHash;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "otp_expires_at", nullable = false, updatable = false)
    private Instant otpExpiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private Instant resetTokenExpiresAt;

    @Column(name = "reset_used_at")
    private Instant resetUsedAt;

    protected PasswordResetChallenge() {}

    public PasswordResetChallenge(
            UUID id, User user, String otpHash, Instant requestedAt, Instant otpExpiresAt) {
        this.id = id;
        this.user = user;
        this.otpHash = otpHash;
        this.requestedAt = requestedAt;
        this.otpExpiresAt = otpExpiresAt;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getOtpHash() { return otpHash; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getOtpVerifiedAt() { return otpVerifiedAt; }
    public Instant getSupersededAt() { return supersededAt; }
    public String getResetTokenHash() { return resetTokenHash; }
    public Instant getResetTokenExpiresAt() { return resetTokenExpiresAt; }
    public Instant getResetUsedAt() { return resetUsedAt; }

    public boolean isOtpUsableAt(Instant now, int maximumAttempts) {
        return supersededAt == null
                && otpVerifiedAt == null
                && resetUsedAt == null
                && failedAttempts < maximumAttempts
                && now.isBefore(otpExpiresAt);
    }

    public void recordFailedAttempt() { failedAttempts++; }

    public void verify(Instant now, String tokenHash, Instant tokenExpiresAt) {
        otpVerifiedAt = now;
        resetTokenHash = tokenHash;
        resetTokenExpiresAt = tokenExpiresAt;
    }

    public void supersede(Instant now) { supersededAt = now; }

    public void consumeReset(Instant now) { resetUsedAt = now; }
}
