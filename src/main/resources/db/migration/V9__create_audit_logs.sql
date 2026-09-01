-- Structured audit log for administrative and class-management actions.

CREATE TABLE audit_logs (
    id               UUID            PRIMARY KEY,
    actor_user_id    UUID,
    actor_email      VARCHAR(320),
    action           VARCHAR(64)     NOT NULL,
    entity_type      VARCHAR(64)     NOT NULL,
    entity_id        UUID,
    summary          VARCHAR(500)    NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id),

    CONSTRAINT ck_audit_logs_action_not_blank CHECK (length(trim(action)) > 0),
    CONSTRAINT ck_audit_logs_entity_type_not_blank CHECK (length(trim(entity_type)) > 0),
    CONSTRAINT ck_audit_logs_summary_not_blank CHECK (length(trim(summary)) > 0)
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_actor_user_id ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
