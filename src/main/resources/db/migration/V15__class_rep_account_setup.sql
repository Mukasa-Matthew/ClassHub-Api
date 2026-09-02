-- Class representative self-registration and single-use account setup tokens.

ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING_SETUP', 'ACTIVE', 'SUSPENDED', 'DISABLED'));

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT ck_users_password_hash_not_blank;
ALTER TABLE users
    ADD CONSTRAINT ck_users_password_for_status CHECK (
        (status = 'PENDING_SETUP' AND password_hash IS NULL)
        OR (status <> 'PENDING_SETUP' AND password_hash IS NOT NULL AND length(trim(password_hash)) > 0)
    );

CREATE UNIQUE INDEX uk_users_phone_number
    ON users (phone_number)
    WHERE phone_number IS NOT NULL;

CREATE TABLE class_rep_account_setups (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    superseded_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_class_rep_account_setups_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_class_rep_account_setups_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_class_rep_account_setups_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_class_rep_account_setups_user
    ON class_rep_account_setups (user_id, issued_at DESC);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'ACCOUNT_SETUP',
        'COURSEWORK_PUBLISHED',
        'ANNOUNCEMENT_PUBLISHED',
        'COURSEWORK_DEADLINE_REMINDER',
        'COURSEWORK_DEADLINE_CHANGED',
        'COURSEWORK_CANCELLED',
        'COURSEWORK_INSTRUCTIONS_UPDATED'
    ));
