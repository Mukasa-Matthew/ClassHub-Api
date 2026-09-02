package com.classhub.notification;

public enum NotificationType {
    ACCOUNT_SETUP(false),
    ACCOUNT_SETUP_COMPLETED(false),
    CLASS_JOIN_REQUESTED(false),
    CLASS_JOIN_APPROVED(false),
    CLASS_JOIN_REJECTED(false),
    CLASS_MEMBER_DEACTIVATED(false),
    CLASS_MEMBER_REACTIVATED(false),
    PASSWORD_RESET_OTP(false),
    PASSWORD_CHANGED(false),
    COURSEWORK_PUBLISHED(true),
    ANNOUNCEMENT_PUBLISHED(true),
    COURSEWORK_DEADLINE_REMINDER(true),
    COURSEWORK_DEADLINE_CHANGED(true),
    COURSEWORK_CANCELLED(true),
    COURSEWORK_INSTRUCTIONS_UPDATED(true);

    private final boolean respectsAcademicChannelPreferences;

    NotificationType(boolean respectsAcademicChannelPreferences) {
        this.respectsAcademicChannelPreferences = respectsAcademicChannelPreferences;
    }

    public boolean respectsAcademicChannelPreferences() {
        return respectsAcademicChannelPreferences;
    }
}
