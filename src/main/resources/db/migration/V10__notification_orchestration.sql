-- Notification orchestration: delivery outbox, preferences, reminder idempotency.

ALTER TABLE notifications
    ADD COLUMN occurrence_key VARCHAR(128) NOT NULL DEFAULT 'default';

ALTER TABLE notifications DROP CONSTRAINT uk_notifications_recipient_type_ref;

ALTER TABLE notifications
    ADD CONSTRAINT uk_notifications_recipient_occurrence
        UNIQUE (recipient_user_id, type, reference_id, occurrence_key);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'COURSEWORK_PUBLISHED',
        'ANNOUNCEMENT_PUBLISHED',
        'COURSEWORK_DEADLINE_REMINDER',
        'COURSEWORK_DEADLINE_CHANGED',
        'COURSEWORK_CANCELLED',
        'COURSEWORK_INSTRUCTIONS_UPDATED'
    ));

CREATE TABLE notification_preferences (
    user_id          UUID            PRIMARY KEY,
    email_enabled    BOOLEAN         NOT NULL DEFAULT TRUE,
    whatsapp_enabled BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ     NOT NULL,
    updated_at       TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE notification_deliveries (
    id                   UUID            PRIMARY KEY,
    notification_id      UUID            NOT NULL,
    user_id              UUID            NOT NULL,
    channel              VARCHAR(32)     NOT NULL,
    status               VARCHAR(32)     NOT NULL,
    attempt_count        INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ,
    last_attempt_at      TIMESTAMPTZ,
    sent_at              TIMESTAMPTZ,
    provider_message_id  VARCHAR(128),
    last_error_code      VARCHAR(64),
    last_error_message   VARCHAR(500),
    created_at           TIMESTAMPTZ     NOT NULL,
    updated_at           TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_notification_deliveries_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT fk_notification_deliveries_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT ck_notification_deliveries_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'WHATSAPP')),
    CONSTRAINT ck_notification_deliveries_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SENT', 'FAILED', 'SKIPPED'
    )),
    CONSTRAINT ck_notification_deliveries_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_deliveries_status_next_attempt
    ON notification_deliveries (status, next_attempt_at);

CREATE INDEX idx_notification_deliveries_notification_id
    ON notification_deliveries (notification_id);

CREATE TABLE notification_reminder_log (
    id              UUID            PRIMARY KEY,
    student_user_id UUID            NOT NULL,
    coursework_id   UUID            NOT NULL,
    reminder_type   VARCHAR(32)     NOT NULL,
    sent_at         TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_notification_reminder_log_student
        FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_reminder_log_coursework
        FOREIGN KEY (coursework_id) REFERENCES coursework (id),

    CONSTRAINT uk_notification_reminder_log_student_coursework_type
        UNIQUE (student_user_id, coursework_id, reminder_type)
);

CREATE INDEX idx_notification_reminder_log_coursework
    ON notification_reminder_log (coursework_id);
