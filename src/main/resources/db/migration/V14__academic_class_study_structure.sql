-- Normalize the academic study structure while preserving existing classes.

ALTER TABLE academic_classes
    ADD COLUMN study_year INTEGER,
    ADD COLUMN semester INTEGER,
    ADD COLUMN academic_year INTEGER;

UPDATE academic_classes
SET programme_name = name
WHERE programme_name IS NULL OR length(trim(programme_name)) = 0;

UPDATE academic_classes
SET study_year = 1,
    semester = 1,
    academic_year = EXTRACT(YEAR FROM created_at AT TIME ZONE 'UTC')::INTEGER;

ALTER TABLE academic_classes
    ALTER COLUMN programme_name SET NOT NULL,
    ALTER COLUMN study_year SET NOT NULL,
    ALTER COLUMN semester SET NOT NULL,
    ALTER COLUMN academic_year SET NOT NULL,
    ADD CONSTRAINT ck_academic_classes_programme_name_not_blank
        CHECK (length(trim(programme_name)) > 0),
    ADD CONSTRAINT ck_academic_classes_study_year
        CHECK (study_year BETWEEN 1 AND 10),
    ADD CONSTRAINT ck_academic_classes_semester
        CHECK (semester BETWEEN 1 AND 4),
    ADD CONSTRAINT ck_academic_classes_academic_year
        CHECK (academic_year BETWEEN 1900 AND 2100);
