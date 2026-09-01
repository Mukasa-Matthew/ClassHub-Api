-- Coursework file attachment metadata (bytes live in FileStorage, not PostgreSQL).

CREATE TABLE coursework_attachments (
    id                   UUID            PRIMARY KEY,
    coursework_id        UUID            NOT NULL,
    original_file_name   VARCHAR(255)    NOT NULL,
    storage_key          VARCHAR(255)    NOT NULL,
    content_type         VARCHAR(150)    NOT NULL,
    file_size            BIGINT          NOT NULL,
    uploaded_by          UUID            NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL,

    CONSTRAINT fk_coursework_attachments_coursework
        FOREIGN KEY (coursework_id) REFERENCES coursework (id),
    CONSTRAINT fk_coursework_attachments_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id),

    CONSTRAINT uk_coursework_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_coursework_attachments_original_name_not_blank
        CHECK (length(trim(original_file_name)) > 0),
    CONSTRAINT ck_coursework_attachments_storage_key_not_blank
        CHECK (length(trim(storage_key)) > 0),
    CONSTRAINT ck_coursework_attachments_content_type_not_blank
        CHECK (length(trim(content_type)) > 0),
    CONSTRAINT ck_coursework_attachments_file_size_positive
        CHECK (file_size > 0)
);

CREATE INDEX idx_coursework_attachments_coursework_id ON coursework_attachments (coursework_id);
