-- Users table for ClassHub (single-class MVP).
-- Email is stored lowercase by the application; uniqueness is enforced case-insensitively.

CREATE TABLE users (
    id              UUID            PRIMARY KEY,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(320)    NOT NULL,
    phone_number    VARCHAR(32),
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(32)     NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('SUPER_ADMIN', 'CLASS_REP', 'STUDENT')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_users_first_name_not_blank CHECK (length(trim(first_name)) > 0),
    CONSTRAINT ck_users_last_name_not_blank CHECK (length(trim(last_name)) > 0),
    CONSTRAINT ck_users_email_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT ck_users_password_hash_not_blank CHECK (length(trim(password_hash)) > 0)
);

CREATE UNIQUE INDEX uk_users_email_lower ON users (LOWER(email));

CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role ON users (role);
