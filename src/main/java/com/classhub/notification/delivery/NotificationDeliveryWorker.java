package com.classhub.notification.delivery;

import com.classhub.notification.DeliveryStatus;
import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationDelivery;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationTemplateService;
import com.classhub.notification.config.NotificationProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationProperties properties;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationMessageResolver messageResolver;
    private final List<NotificationDeliveryAdapter> adapters;
    private final Clock clock;

    public NotificationDeliveryWorker(
            NotificationProperties properties,
            NotificationDeliveryRepository deliveryRepository,
            NotificationMessageResolver messageResolver,
            List<NotificationDeliveryAdapter> adapters,
            Clock clock) {
        this.properties = properties;
        this.deliveryRepository = deliveryRepository;
        this.messageResolver = messageResolver;
        this.adapters = adapters;
        this.clock = clock;
    }

    @Transactional
    public void processPendingBatch() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        int batchSize = properties.getDelivery().getBatchSize();
        List<UUID> ids = deliveryRepository.lockPendingIds(now, batchSize);
        if (ids.isEmpty()) {
            return;
        }
        List<NotificationDelivery> batch = deliveryRepository.findAllById(ids);
        Map<NotificationChannel, NotificationDeliveryAdapter> adapterMap = new HashMap<>();
        for (NotificationDeliveryAdapter adapter : adapters) {
            adapterMap.put(adapter.channel(), adapter);
        }
        for (NotificationDelivery delivery : batch) {
            if (delivery.getStatus() != DeliveryStatus.PENDING) {
                continue;
            }
            processOne(delivery, adapterMap, now);
        }
    }

    private void processOne(
            NotificationDelivery delivery,
            Map<NotificationChannel, NotificationDeliveryAdapter> adapterMap,
            Instant now) {
        if (delivery.getAttemptCount() >= properties.getDelivery().getMaxAttempts()) {
            delivery.markFailed(now, "MAX_ATTEMPTS", "Maximum delivery attempts exceeded", null);
            return;
        }
        delivery.markProcessing(now);
        NotificationDeliveryAdapter adapter = adapterMap.get(delivery.getChannel());
        if (adapter == null) {
            delivery.markSkipped("NO_ADAPTER", "No delivery adapter registered");
            return;
        }
        NotificationMessage message = messageResolver.fromDelivery(delivery);
        NotificationDeliveryRequest request =
                buildRequest(delivery, message);
        try {
            DeliveryResult result = adapter.send(request);
            if (result.skipped()) {
                delivery.markSkipped(result.errorCode(), result.safeErrorMessage());
            } else if (result.success()) {
                delivery.markSent(now, result.providerMessageId());
            } else {
                scheduleRetryOrFail(delivery, now, result.errorCode(), result.safeErrorMessage());
            }
        } catch (RuntimeException ex) {
            log.warn("Delivery {} failed: {}", delivery.getId(), ex.getMessage());
            scheduleRetryOrFail(delivery, now, "DELIVERY_ERROR", "Delivery processing failed");
        }
    }

    private void scheduleRetryOrFail(
            NotificationDelivery delivery, Instant now, String code, String message) {
        int attempt = delivery.getAttemptCount();
        int maxAttempts = properties.getDelivery().getMaxAttempts();
        if (attempt >= maxAttempts) {
            delivery.markFailed(now, code, message, null);
            return;
        }
        List<Duration> backoff = properties.getDelivery().getRetryBackoff();
        Duration delay = attempt > 0 && attempt <= backoff.size()
                ? backoff.get(attempt - 1)
                : backoff.get(backoff.size() - 1);
        delivery.scheduleRetry(now.plus(delay), now, code, message);
    }

    private NotificationDeliveryRequest buildRequest(
            NotificationDelivery delivery, NotificationMessage message) {
        var user = delivery.getUser();
        return new NotificationDeliveryRequest(
                delivery.getId(),
                delivery.getNotification().getId(),
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFirstName(),
                delivery.getChannel(),
                message.eventType(),
                message);
    }
}
