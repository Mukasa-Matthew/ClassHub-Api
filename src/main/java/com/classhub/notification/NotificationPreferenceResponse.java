package com.classhub.notification;

public record NotificationPreferenceResponse(
        boolean emailEnabled, boolean whatsappEnabled, boolean pushEnabled) {

    static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.isEmailEnabled(), preference.isWhatsappEnabled(), preference.isPushEnabled());
    }
}
