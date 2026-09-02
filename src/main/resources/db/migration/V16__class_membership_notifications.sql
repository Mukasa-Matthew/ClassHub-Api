-- Student class join lifecycle notification events.

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'ACCOUNT_SETUP',
        'CLASS_JOIN_REQUESTED',
        'CLASS_JOIN_APPROVED',
        'COURSEWORK_PUBLISHED',
        'ANNOUNCEMENT_PUBLISHED',
        'COURSEWORK_DEADLINE_REMINDER',
        'COURSEWORK_DEADLINE_CHANGED',
        'COURSEWORK_CANCELLED',
        'COURSEWORK_INSTRUCTIONS_UPDATED'
    ));
