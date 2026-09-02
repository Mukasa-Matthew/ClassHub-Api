package com.classhub.notification.push;

public record WebPushTransportRequest(
        String endpoint,
        String p256dh,
        String auth,
        String payload,
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject) {}
