-- Coursework for the single ClassHub class (no attachments/progress in this migration).

CREATE TABLE coursework (
    id                UUID            PRIMARY KEY,
    course_unit_id    UUID            NOT NULL,
    title             VARCHAR(300)    NOT NULL,
    description       TEXT            NOT NULL,
    instructions      TEXT,
    type              VARCHAR(32)     NOT NULL,
    issued_at         TIMESTAMPTZ,
    due_at            TIMESTAMPTZ     NOT NULL,
    weight            NUMERIC(6, 2),
    source_type       VARCHAR(32)     NOT NULL,
    source_url        VARCHAR(2048),
    source_label      VARCHAR(200),
    status            VARCHAR(32)     NOT NULL,
    created_by        UUID            NOT NULL,
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ     NOT NULL,
    updated_at        TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_coursework_course_unit
        FOREIGN KEY (course_unit_id) REFERENCES course_units (id),
    CONSTRAINT fk_coursework_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),

    CONSTRAINT ck_coursework_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_coursework_description_not_blank CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_coursework_type CHECK (type IN (
        'ASSIGNMENT', 'COURSEWORK', 'TEST', 'QUIZ', 'PRESENTATION',
        'GROUP_PROJECT', 'RESEARCH', 'PRACTICAL', 'OTHER'
    )),
    CONSTRAINT ck_coursework_source_type CHECK (source_type IN (
        'DIRECT_ENTRY', 'DIRECT_UPLOAD', 'MOODLE', 'CANVAS', 'BLACKBOARD',
        'GOOGLE_CLASSROOM', 'GOOGLE_DRIVE', 'LECTURER_EMAIL', 'PHYSICAL_HANDOUT',
        'EXTERNAL_LINK', 'OTHER'
    )),
    CONSTRAINT ck_coursework_status CHECK (status IN (
        'DRAFT', 'PUBLISHED', 'CANCELLED', 'ARCHIVED'
    )),
    CONSTRAINT ck_coursework_weight_range CHECK (
        weight IS NULL OR (weight > 0 AND weight <= 100)
    ),
    CONSTRAINT ck_coursework_due_after_issued CHECK (
        issued_at IS NULL OR due_at > issued_at
    )
);

CREATE INDEX idx_coursework_course_unit_id ON coursework (course_unit_id);
CREATE INDEX idx_coursework_due_at ON coursework (due_at);
CREATE INDEX idx_coursework_status ON coursework (status);
