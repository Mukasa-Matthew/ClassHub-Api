-- Private lecture notes and separately stored AI outputs (raw content is never overwritten).

CREATE TABLE lecture_notes (
    id                   UUID            PRIMARY KEY,
    student_id           UUID            NOT NULL,
    course_unit_id       UUID            NOT NULL,
    title                VARCHAR(300),
    raw_content          TEXT            NOT NULL,
    status               VARCHAR(32)     NOT NULL,
    lecture_started_at   TIMESTAMPTZ,
    lecture_ended_at     TIMESTAMPTZ,
    created_at           TIMESTAMPTZ     NOT NULL,
    updated_at           TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_lecture_notes_student
        FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_lecture_notes_course_unit
        FOREIGN KEY (course_unit_id) REFERENCES course_units (id),

    CONSTRAINT ck_lecture_notes_raw_content_not_blank
        CHECK (length(trim(raw_content)) > 0),
    CONSTRAINT ck_lecture_notes_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'))
);

CREATE INDEX idx_lecture_notes_student_id ON lecture_notes (student_id);
CREATE INDEX idx_lecture_notes_student_course_unit ON lecture_notes (student_id, course_unit_id);
CREATE INDEX idx_lecture_notes_student_status ON lecture_notes (student_id, status);

CREATE TABLE lecture_note_ai_outputs (
    id                 UUID            PRIMARY KEY,
    lecture_note_id    UUID            NOT NULL,
    operation          VARCHAR(32)     NOT NULL,
    content            TEXT            NOT NULL,
    model_provider     VARCHAR(100),
    model_name         VARCHAR(100),
    created_at         TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_lecture_note_ai_outputs_note
        FOREIGN KEY (lecture_note_id) REFERENCES lecture_notes (id),

    CONSTRAINT ck_lecture_note_ai_outputs_content_not_blank
        CHECK (length(trim(content)) > 0),
    CONSTRAINT ck_lecture_note_ai_outputs_operation CHECK (operation IN (
        'ORGANIZE', 'EXPAND', 'SUMMARIZE', 'EXPLAIN', 'CORRECT', 'STUDY_GUIDE'
    ))
);

CREATE INDEX idx_lecture_note_ai_outputs_note_id ON lecture_note_ai_outputs (lecture_note_id);
