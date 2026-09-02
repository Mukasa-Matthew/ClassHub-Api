-- Secure, single-use password recovery challenges and authorization tokens.

CREATE TABLE password_reset_challenges (
    id                      UUID        PRIMARY KEY,
    user_id                 UUID        NOT NULL,
    otp_hash                VARCHAR(64) NOT NULL,
    requested_at            TIMESTAMPTZ NOT NULL,
    otp_expires_at          TIMESTAMPTZ NOT NULL,
    failed_attempts         INTEGER     NOT NULL DEFAULT 0,
    otp_verified_at         TIMESTAMPTZ,
    superseded_at           TIMESTAMPTZ,
    reset_token_hash        VARCHAR(64),
    reset_token_expires_at  TIMESTAMPTZ,
    reset_used_at           TIMESTAMPTZ,

    CONSTRAINT fk_password_reset_challenges_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_password_reset_challenges_reset_token_hash UNIQUE (reset_token_hash),
    CONSTRAINT ck_password_reset_challenges_otp_expiry CHECK (otp_expires_at > requested_at),
    CONSTRAINT ck_password_reset_challenges_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_password_reset_challenges_token_pair CHECK (
        (reset_token_hash IS NULL AND reset_token_expires_at IS NULL)
        OR (reset_token_hash IS NOT NULL AND reset_token_expires_at IS NOT NULL)
    )
);

CREATE INDEX idx_password_reset_challenges_user_requested
    ON password_reset_challenges (user_id, requested_at DESC);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'ACCOUNT_SETUP',
        'CLASS_JOIN_REQUESTED',
        'CLASS_JOIN_APPROVED',
        'PASSWORD_RESET_OTP',
        'PASSWORD_CHANGED',
        'COURSEWORK_PUBLISHED',
        'ANNOUNCEMENT_PUBLISHED',
        'COURSEWORK_DEADLINE_REMINDER',
        'COURSEWORK_DEADLINE_CHANGED',
        'COURSEWORK_CANCELLED',
        'COURSEWORK_INSTRUCTIONS_UPDATED'
    ));
