package com.classhub.notification;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull Boolean emailEnabled,
        @NotNull Boolean whatsappEnabled,
        Boolean pushEnabled) {
}
