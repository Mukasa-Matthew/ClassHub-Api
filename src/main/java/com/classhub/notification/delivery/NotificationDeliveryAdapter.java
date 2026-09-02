package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;

public interface NotificationDeliveryAdapter {

    NotificationChannel channel();

    DeliveryResult send(NotificationDeliveryRequest request);
}
