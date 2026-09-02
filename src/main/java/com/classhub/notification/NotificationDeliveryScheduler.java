package com.classhub.notification;

import com.classhub.notification.delivery.NotificationDeliveryWorker;
import com.classhub.notification.config.NotificationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryScheduler {

    private final NotificationProperties properties;
    private final NotificationDeliveryWorker worker;

    public NotificationDeliveryScheduler(
            NotificationProperties properties, NotificationDeliveryWorker worker) {
        this.properties = properties;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${classhub.notifications.delivery.scheduler-interval:PT1M}")
    public void processDeliveries() {
        if (!properties.isEnabled()) {
            return;
        }
        worker.processPendingBatch();
    }
}
