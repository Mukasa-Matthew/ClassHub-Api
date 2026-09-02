-- Course units for the single ClassHub class.

CREATE TABLE course_units (
    id               UUID            PRIMARY KEY,
    code             VARCHAR(50),
    name             VARCHAR(200)    NOT NULL,
    normalized_name  VARCHAR(200)    NOT NULL,
    lecturer_name    VARCHAR(200),
    description      TEXT,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ     NOT NULL,
    updated_at       TIMESTAMPTZ     NOT NULL,
    CONSTRAINT uk_course_units_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_course_units_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_course_units_normalized_name_not_blank CHECK (length(trim(normalized_name)) > 0)
);

CREATE INDEX idx_course_units_active ON course_units (active);
