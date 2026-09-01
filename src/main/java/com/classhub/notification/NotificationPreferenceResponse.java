package com.classhub.notification;

public record NotificationPreferenceResponse(boolean emailEnabled, boolean whatsappEnabled) {

    static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.isEmailEnabled(), preference.isWhatsappEnabled());
    }
}
