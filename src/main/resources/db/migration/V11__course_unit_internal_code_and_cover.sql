-- Internal ClassHub course-unit codes and optional cover image metadata.

CREATE SEQUENCE course_unit_internal_code_seq START WITH 1 INCREMENT BY 1 NO MAXVALUE;

ALTER TABLE course_units
    ADD COLUMN internal_code VARCHAR(20);

DO $$
DECLARE
    unit_row RECORD;
BEGIN
    FOR unit_row IN
        SELECT id FROM course_units ORDER BY created_at ASC, id ASC
    LOOP
        UPDATE course_units
        SET internal_code = 'CU-' || LPAD(nextval('course_unit_internal_code_seq')::text, 6, '0')
        WHERE id = unit_row.id;
    END LOOP;
END $$;

ALTER TABLE course_units
    ALTER COLUMN internal_code SET NOT NULL;

ALTER TABLE course_units
    ADD CONSTRAINT uk_course_units_internal_code UNIQUE (internal_code);

CREATE INDEX idx_course_units_internal_code ON course_units (internal_code);

ALTER TABLE course_units
    ADD COLUMN cover_image_storage_key VARCHAR(256),
    ADD COLUMN cover_image_original_name VARCHAR(255),
    ADD COLUMN cover_image_content_type VARCHAR(100),
    ADD COLUMN cover_image_size_bytes BIGINT,
    ADD COLUMN cover_image_updated_at TIMESTAMPTZ;

ALTER TABLE course_units
    ADD CONSTRAINT ck_course_units_cover_image_size_positive
        CHECK (cover_image_size_bytes IS NULL OR cover_image_size_bytes > 0);
