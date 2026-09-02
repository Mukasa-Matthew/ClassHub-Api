-- Academic classes, memberships, and class scoping for course units and announcements.

CREATE TABLE academic_classes (
    id              UUID            PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    programme_name  VARCHAR(200),
    programme_code  VARCHAR(50),
    join_code       VARCHAR(16)     NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,

    CONSTRAINT uk_academic_classes_join_code UNIQUE (join_code),
    CONSTRAINT ck_academic_classes_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_academic_classes_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_academic_classes_status ON academic_classes (status);

ALTER TABLE users
    ADD COLUMN registration_number VARCHAR(64);

CREATE UNIQUE INDEX uk_users_registration_number_lower
    ON users (LOWER(registration_number))
    WHERE registration_number IS NOT NULL;

INSERT INTO academic_classes (
    id, name, programme_name, programme_code, join_code, status, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'ClassHub Class',
    NULL,
    NULL,
    'TMP000',
    'ACTIVE',
    NOW(),
    NOW()
);

DO $$
DECLARE
    chars text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    code text := '';
    i int;
BEGIN
    FOR i IN 1..6 LOOP
        code := code || substr(chars, 1 + floor(random() * 32)::int, 1);
    END LOOP;
    UPDATE academic_classes SET join_code = code WHERE join_code = 'TMP000';
END $$;

ALTER TABLE course_units
    ADD COLUMN academic_class_id UUID;

UPDATE course_units cu
SET academic_class_id = (SELECT id FROM academic_classes ORDER BY created_at LIMIT 1);

ALTER TABLE course_units
    ALTER COLUMN academic_class_id SET NOT NULL;

ALTER TABLE course_units
    ADD CONSTRAINT fk_course_units_academic_class
        FOREIGN KEY (academic_class_id) REFERENCES academic_classes (id);

CREATE INDEX idx_course_units_academic_class_id ON course_units (academic_class_id);

ALTER TABLE course_units DROP CONSTRAINT uk_course_units_normalized_name;

ALTER TABLE course_units
    ADD CONSTRAINT uk_course_units_class_normalized_name
        UNIQUE (academic_class_id, normalized_name);

ALTER TABLE announcements
    ADD COLUMN academic_class_id UUID;

UPDATE announcements a
SET academic_class_id = (SELECT id FROM academic_classes ORDER BY created_at LIMIT 1);

ALTER TABLE announcements
    ALTER COLUMN academic_class_id SET NOT NULL;

ALTER TABLE announcements
    ADD CONSTRAINT fk_announcements_academic_class
        FOREIGN KEY (academic_class_id) REFERENCES academic_classes (id);

CREATE INDEX idx_announcements_academic_class_id ON announcements (academic_class_id);

CREATE TABLE class_memberships (
    id                  UUID            PRIMARY KEY,
    academic_class_id   UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    membership_role     VARCHAR(32)     NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    requested_at        TIMESTAMPTZ     NOT NULL,
    approved_at         TIMESTAMPTZ,
    approved_by_user_id UUID,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_class_memberships_class
        FOREIGN KEY (academic_class_id) REFERENCES academic_classes (id),
    CONSTRAINT fk_class_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_class_memberships_approved_by
        FOREIGN KEY (approved_by_user_id) REFERENCES users (id),

    CONSTRAINT uk_class_memberships_class_user UNIQUE (academic_class_id, user_id),
    CONSTRAINT ck_class_memberships_role CHECK (membership_role IN ('STUDENT', 'CLASS_REP')),
    CONSTRAINT ck_class_memberships_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'INACTIVE'))
);

CREATE INDEX idx_class_memberships_class_status ON class_memberships (academic_class_id, status);
CREATE INDEX idx_class_memberships_user_status ON class_memberships (user_id, status);

INSERT INTO class_memberships (
    id, academic_class_id, user_id, membership_role, status,
    requested_at, approved_at, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    (SELECT id FROM academic_classes ORDER BY created_at LIMIT 1),
    u.id,
    CASE WHEN u.role = 'CLASS_REP' THEN 'CLASS_REP' ELSE 'STUDENT' END,
    'ACTIVE',
    u.created_at,
    u.created_at,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.role IN ('STUDENT', 'CLASS_REP')
  AND u.status = 'ACTIVE';
