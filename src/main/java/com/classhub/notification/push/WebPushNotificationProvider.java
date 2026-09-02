package com.classhub.notification.push;

import com.classhub.notification.delivery.DeliveryResult;

public interface WebPushNotificationProvider {
    DeliveryResult send(WebPushSendCommand command);
}
