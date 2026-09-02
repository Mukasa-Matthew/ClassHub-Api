-- Additive Web Push channel and per-user browser/device subscriptions.

ALTER TABLE notification_preferences
    ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE push_subscriptions (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,
    endpoint        VARCHAR(2048) NOT NULL,
    endpoint_hash   VARCHAR(64)   NOT NULL,
    p256dh_key      VARCHAR(180)  NOT NULL,
    auth_key        VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_push_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_push_subscriptions_endpoint_hash UNIQUE (endpoint_hash),
    CONSTRAINT ck_push_subscriptions_endpoint_not_blank CHECK (length(trim(endpoint)) > 0),
    CONSTRAINT ck_push_subscriptions_p256dh_not_blank CHECK (length(trim(p256dh_key)) > 0),
    CONSTRAINT ck_push_subscriptions_auth_not_blank CHECK (length(trim(auth_key)) > 0)
);

CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions (user_id);

ALTER TABLE notification_deliveries DROP CONSTRAINT ck_notification_deliveries_channel;
ALTER TABLE notification_deliveries
    ADD CONSTRAINT ck_notification_deliveries_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'WHATSAPP', 'PUSH'));
