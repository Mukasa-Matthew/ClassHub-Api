package com.classhub.notification.delivery;

import com.classhub.notification.NotificationChannel;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private final Clock clock;

    public InAppNotificationDeliveryAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public DeliveryResult send(NotificationDeliveryRequest request) {
        return DeliveryResult.sent("in-app:" + request.notificationId());
    }
}
