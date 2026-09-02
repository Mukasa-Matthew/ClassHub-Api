-- Extend the constrained notification catalogue with the remaining account and
-- class-membership lifecycle events. Existing notification data is unchanged.

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'ACCOUNT_SETUP',
        'ACCOUNT_SETUP_COMPLETED',
        'CLASS_JOIN_REQUESTED',
        'CLASS_JOIN_APPROVED',
        'CLASS_JOIN_REJECTED',
        'CLASS_MEMBER_DEACTIVATED',
        'CLASS_MEMBER_REACTIVATED',
        'PASSWORD_RESET_OTP',
        'PASSWORD_CHANGED',
        'COURSEWORK_PUBLISHED',
        'ANNOUNCEMENT_PUBLISHED',
        'COURSEWORK_DEADLINE_REMINDER',
        'COURSEWORK_DEADLINE_CHANGED',
        'COURSEWORK_CANCELLED',
        'COURSEWORK_INSTRUCTIONS_UPDATED'
    ));
