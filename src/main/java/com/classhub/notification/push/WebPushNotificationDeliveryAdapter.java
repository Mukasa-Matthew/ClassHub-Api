package com.classhub.notification.push;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.DeliveryResult;
import com.classhub.notification.delivery.NotificationDeliveryAdapter;
import com.classhub.notification.delivery.NotificationDeliveryRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WebPushNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private final NotificationProperties properties;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushNotificationProvider provider;

    public WebPushNotificationDeliveryAdapter(
            NotificationProperties properties,
            PushSubscriptionRepository subscriptionRepository,
            WebPushNotificationProvider provider) {
        this.properties = properties;
        this.subscriptionRepository = subscriptionRepository;
        this.provider = provider;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public DeliveryResult send(NotificationDeliveryRequest request) {
        if (!properties.getPush().isEnabled()) {
            return DeliveryResult.skipped("PROVIDER_DISABLED", "Web Push provider is disabled");
        }
        if (!properties.getPush().isConfigured()) {
            return DeliveryResult.failed(
                    "PROVIDER_NOT_CONFIGURED", "Web Push VAPID configuration is incomplete");
        }

        List<PushSubscription> subscriptions =
                subscriptionRepository.findByUserId(request.recipientUserId());
        if (subscriptions.isEmpty()) {
            return DeliveryResult.skipped("NO_SUBSCRIPTION", "No push subscription available");
        }

        int sent = 0;
        int expired = 0;
        DeliveryResult retryableFailure = null;
        for (PushSubscription subscription : subscriptions) {
            DeliveryResult result;
            try {
                result = provider.send(command(request, subscription));
            } catch (RuntimeException ex) {
                if (retryableFailure == null) {
                    retryableFailure = DeliveryResult.failed(
                            "WEB_PUSH_DELIVERY_ERROR", "Web Push delivery failed");
                }
                continue;
            }
            if (result.success()) {
                sent++;
                continue;
            }
            if (VapidWebPushNotificationProvider.EXPIRED_SUBSCRIPTION.equals(result.errorCode())) {
                subscriptionRepository.delete(subscription);
                expired++;
                continue;
            }
            if (!result.skipped() && retryableFailure == null) {
                retryableFailure = result;
            }
        }

        if (retryableFailure != null) {
            return retryableFailure;
        }
        if (sent > 0) {
            return DeliveryResult.sent(request.deliveryId() + ":devices=" + sent);
        }
        if (expired == subscriptions.size()) {
            return DeliveryResult.skipped(
                    "NO_ACTIVE_SUBSCRIPTION", "All Web Push subscriptions have expired");
        }
        return DeliveryResult.skipped("NO_SUBSCRIPTION", "No eligible push subscription available");
    }

    private WebPushSendCommand command(
            NotificationDeliveryRequest request, PushSubscription subscription) {
        String actionUrl = absoluteActionUrl(request.message().actionPath());
        return new WebPushSendCommand(
                request.deliveryId(),
                subscription.getEndpoint(),
                subscription.getP256dhKey(),
                subscription.getAuthKey(),
                "ClassHub",
                request.message().shortText(),
                actionUrl,
                request.eventType().name(),
                referenceId(request));
    }

    private String absoluteActionUrl(String actionPath) {
        String base = properties.getWebBaseUrl();
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = actionPath == null || actionPath.isBlank()
                ? "/"
                : actionPath.startsWith("/") ? actionPath : "/" + actionPath;
        return normalizedBase + normalizedPath;
    }

    private static String referenceId(NotificationDeliveryRequest request) {
        if (request.message().courseworkId() != null) {
            return request.message().courseworkId().toString();
        }
        if (request.message().announcementId() != null) {
            return request.message().announcementId().toString();
        }
        if (request.message().courseUnitId() != null) {
            return request.message().courseUnitId().toString();
        }
        return null;
    }
}
