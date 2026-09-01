-- Announcements and in-app notifications (class-wide MVP).

CREATE TABLE announcements (
    id            UUID            PRIMARY KEY,
    title         VARCHAR(300)    NOT NULL,
    content       TEXT            NOT NULL,
    status        VARCHAR(32)     NOT NULL,
    created_by    UUID            NOT NULL,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ     NOT NULL,
    updated_at    TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_announcements_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),

    CONSTRAINT ck_announcements_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_announcements_content_not_blank CHECK (length(trim(content)) > 0),
    CONSTRAINT ck_announcements_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_announcements_status ON announcements (status);
CREATE INDEX idx_announcements_published_at ON announcements (published_at);

CREATE TABLE notifications (
    id                   UUID            PRIMARY KEY,
    recipient_user_id    UUID            NOT NULL,
    type                 VARCHAR(64)     NOT NULL,
    title                VARCHAR(300)    NOT NULL,
    message              TEXT            NOT NULL,
    reference_id         UUID,
    reference_type       VARCHAR(64),
    is_read              BOOLEAN         NOT NULL DEFAULT FALSE,
    read_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users (id),

    CONSTRAINT ck_notifications_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_notifications_message_not_blank CHECK (length(trim(message)) > 0),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'COURSEWORK_PUBLISHED', 'ANNOUNCEMENT_PUBLISHED'
    )),
    CONSTRAINT ck_notifications_read_at CHECK (
        (is_read = TRUE AND read_at IS NOT NULL)
        OR (is_read = FALSE AND read_at IS NULL)
    ),
    CONSTRAINT uk_notifications_recipient_type_ref
        UNIQUE (recipient_user_id, type, reference_id)
);

CREATE INDEX idx_notifications_recipient_user_id ON notifications (recipient_user_id);
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
