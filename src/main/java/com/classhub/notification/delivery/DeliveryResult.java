package com.classhub.notification.delivery;

public record DeliveryResult(
        boolean success,
        String providerMessageId,
        String providerStatus,
        String errorCode,
        String safeErrorMessage,
        boolean skipped) {

    public static DeliveryResult sent(String providerMessageId) {
        return new DeliveryResult(true, providerMessageId, "SENT", null, null, false);
    }

    public static DeliveryResult skipped(String code, String message) {
        return new DeliveryResult(false, null, "SKIPPED", code, message, true);
    }

    public static DeliveryResult failed(String code, String message) {
        return new DeliveryResult(false, null, "FAILED", code, message, false);
    }
}
