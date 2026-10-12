package com.classhub.common.api;

public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public static final String USER_ALREADY_EXISTS = "USER_ALREADY_EXISTS";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String INVALID_USER_DATA = "INVALID_USER_DATA";

    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String INVALID_ACCOUNT_SETUP_TOKEN = "INVALID_ACCOUNT_SETUP_TOKEN";
    public static final String EXPIRED_ACCOUNT_SETUP_TOKEN = "EXPIRED_ACCOUNT_SETUP_TOKEN";
    public static final String ONBOARDING_RATE_LIMITED = "ONBOARDING_RATE_LIMITED";
    public static final String INVALID_PASSWORD_RESET_OTP = "INVALID_PASSWORD_RESET_OTP";
    public static final String INVALID_PASSWORD_RESET_TOKEN = "INVALID_PASSWORD_RESET_TOKEN";
    public static final String PASSWORD_RESET_RATE_LIMITED = "PASSWORD_RESET_RATE_LIMITED";
    public static final String FORBIDDEN = "FORBIDDEN";

    public static final String INVALID_ROLE_CHANGE = "INVALID_ROLE_CHANGE";
    public static final String INVALID_STATUS_CHANGE = "INVALID_STATUS_CHANGE";
    public static final String CANNOT_MODIFY_SELF = "CANNOT_MODIFY_SELF";
    public static final String LAST_SUPER_ADMIN_PROTECTED = "LAST_SUPER_ADMIN_PROTECTED";

    public static final String COURSE_UNIT_NOT_FOUND = "COURSE_UNIT_NOT_FOUND";
    public static final String COURSE_UNIT_ALREADY_EXISTS = "COURSE_UNIT_ALREADY_EXISTS";
    public static final String INVALID_COURSE_UNIT_DATA = "INVALID_COURSE_UNIT_DATA";
    public static final String COURSE_UNIT_COVER_NOT_FOUND = "COURSE_UNIT_COVER_NOT_FOUND";

    public static final String COURSEWORK_NOT_FOUND = "COURSEWORK_NOT_FOUND";
    public static final String INVALID_COURSEWORK_DATA = "INVALID_COURSEWORK_DATA";
    public static final String INVALID_COURSEWORK_STATE = "INVALID_COURSEWORK_STATE";
    public static final String INVALID_COURSEWORK_DEADLINE = "INVALID_COURSEWORK_DEADLINE";
    public static final String INVALID_COURSEWORK_SOURCE = "INVALID_COURSEWORK_SOURCE";
    public static final String COURSEWORK_PROGRESS_NOT_ALLOWED = "COURSEWORK_PROGRESS_NOT_ALLOWED";
    public static final String INVALID_COURSEWORK_PROGRESS = "INVALID_COURSEWORK_PROGRESS";

    public static final String ANNOUNCEMENT_NOT_FOUND = "ANNOUNCEMENT_NOT_FOUND";
    public static final String INVALID_ANNOUNCEMENT_DATA = "INVALID_ANNOUNCEMENT_DATA";
    public static final String INVALID_ANNOUNCEMENT_STATE = "INVALID_ANNOUNCEMENT_STATE";

    public static final String NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";
    public static final String INVALID_NOTIFICATION_DATA = "INVALID_NOTIFICATION_DATA";

    public static final String ATTACHMENT_NOT_FOUND = "ATTACHMENT_NOT_FOUND";
    public static final String ATTACHMENT_NOT_ALLOWED = "ATTACHMENT_NOT_ALLOWED";
    public static final String INVALID_ATTACHMENT = "INVALID_ATTACHMENT";
    public static final String ATTACHMENT_TOO_LARGE = "ATTACHMENT_TOO_LARGE";
    public static final String ATTACHMENT_STORAGE_ERROR = "ATTACHMENT_STORAGE_ERROR";

    public static final String LECTURE_NOTE_NOT_FOUND = "LECTURE_NOTE_NOT_FOUND";
    public static final String INVALID_LECTURE_NOTE_DATA = "INVALID_LECTURE_NOTE_DATA";
    public static final String INVALID_LECTURE_NOTE_STATE = "INVALID_LECTURE_NOTE_STATE";
    public static final String AI_NOTE_PROCESSING_FAILED = "AI_NOTE_PROCESSING_FAILED";
    public static final String INVALID_AI_NOTE_OPERATION = "INVALID_AI_NOTE_OPERATION";

    public static final String CLASS_NOT_FOUND = "CLASS_NOT_FOUND";
    public static final String INVALID_CLASS_DATA = "INVALID_CLASS_DATA";
    public static final String INVALID_JOIN_CODE = "INVALID_JOIN_CODE";
    public static final String CLASS_MEMBERSHIP_REQUIRED = "CLASS_MEMBERSHIP_REQUIRED";
    public static final String CLASS_MEMBERSHIP_NOT_FOUND = "CLASS_MEMBERSHIP_NOT_FOUND";
    public static final String CLASS_MEMBERSHIP_ALREADY_EXISTS = "CLASS_MEMBERSHIP_ALREADY_EXISTS";
    public static final String INVALID_CLASS_MEMBERSHIP_STATE = "INVALID_CLASS_MEMBERSHIP_STATE";

    private ErrorCodes() {
    }
}
