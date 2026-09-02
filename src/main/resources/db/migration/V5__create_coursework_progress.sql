-- Private per-student coursework progress (personal tracking, not grading).

CREATE TABLE coursework_progress (
    id                 UUID            PRIMARY KEY,
    coursework_id      UUID            NOT NULL,
    student_id         UUID            NOT NULL,
    progress_status    VARCHAR(32)     NOT NULL,
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ     NOT NULL,
    updated_at         TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_coursework_progress_coursework
        FOREIGN KEY (coursework_id) REFERENCES coursework (id),
    CONSTRAINT fk_coursework_progress_student
        FOREIGN KEY (student_id) REFERENCES users (id),

    CONSTRAINT uk_coursework_progress_coursework_student UNIQUE (coursework_id, student_id),

    CONSTRAINT ck_coursework_progress_status CHECK (progress_status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'
    )),
    CONSTRAINT ck_coursework_progress_completed_at CHECK (
        (progress_status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (progress_status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX idx_coursework_progress_student_id ON coursework_progress (student_id);
CREATE INDEX idx_coursework_progress_coursework_id ON coursework_progress (coursework_id);
