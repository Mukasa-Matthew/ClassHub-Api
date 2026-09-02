package com.classhub.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationType;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.DeliveryResult;
import com.classhub.notification.delivery.NotificationDeliveryRequest;
import com.classhub.notification.delivery.NotificationMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WebPushNotificationDeliveryAdapterTest {

    @Mock private PushSubscriptionRepository repository;
    @Mock private WebPushNotificationProvider provider;

    private NotificationProperties properties;
    private WebPushNotificationDeliveryAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new NotificationProperties();
        properties.setWebBaseUrl("https://app.classhub.test/");
        properties.getPush().setEnabled(true);
        properties.getPush().setVapidPublicKey("public");
        properties.getPush().setVapidPrivateKey("private");
        properties.getPush().setSubject("mailto:push@classhub.test");
        adapter = new WebPushNotificationDeliveryAdapter(properties, repository, provider);
    }

    @Test
    void fansOutToEveryRegisteredDevice() {
        when(repository.findByUserId(any())).thenReturn(List.of(subscription("one"), subscription("two")));
        when(provider.send(any())).thenReturn(DeliveryResult.sent("accepted"));

        DeliveryResult result = adapter.send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).contains("devices=2");
        verify(provider, times(2)).send(any());
    }

    @Test
    void removesNotFoundSubscriptionAndStillSendsOtherDevice() {
        PushSubscription expired = subscription("expired-404");
        PushSubscription active = subscription("active");
        when(repository.findByUserId(any())).thenReturn(List.of(expired, active));
        when(provider.send(any()))
                .thenReturn(DeliveryResult.failed(
                        VapidWebPushNotificationProvider.EXPIRED_SUBSCRIPTION, "expired"))
                .thenReturn(DeliveryResult.sent("accepted"));

        DeliveryResult result = adapter.send(request());

        assertThat(result.success()).isTrue();
        verify(repository).delete(expired);
        verify(provider, times(2)).send(any());
    }

    @Test
    void removesGoneSubscriptionWithoutRetryingIt() {
        PushSubscription expired = subscription("expired-410");
        when(repository.findByUserId(any())).thenReturn(List.of(expired));
        when(provider.send(any())).thenReturn(DeliveryResult.failed(
                VapidWebPushNotificationProvider.EXPIRED_SUBSCRIPTION, "expired"));

        DeliveryResult result = adapter.send(request());

        assertThat(result.skipped()).isTrue();
        assertThat(result.errorCode()).isEqualTo("NO_ACTIVE_SUBSCRIPTION");
        verify(repository).delete(expired);
    }

    @Test
    void transientFailureIsReturnedForExistingOutboxRetryHandling() {
        when(repository.findByUserId(any())).thenReturn(List.of(subscription("temporary")));
        when(provider.send(any()))
                .thenReturn(DeliveryResult.failed("WEB_PUSH_UNAVAILABLE", "temporarily unavailable"));

        DeliveryResult result = adapter.send(request());

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_UNAVAILABLE");
        verify(repository, never()).delete(any());
    }

    @Test
    void failedDeviceDoesNotPreventAttemptingRemainingDevices() {
        when(repository.findByUserId(any())).thenReturn(List.of(subscription("failed"), subscription("active")));
        when(provider.send(any()))
                .thenReturn(DeliveryResult.failed("WEB_PUSH_UNAVAILABLE", "temporary"))
                .thenReturn(DeliveryResult.sent("accepted"));

        DeliveryResult result = adapter.send(request());

        assertThat(result.errorCode()).isEqualTo("WEB_PUSH_UNAVAILABLE");
        verify(provider, times(2)).send(any());
    }

    @Test
    void userWithoutSubscriptionsIsSkipped() {
        when(repository.findByUserId(any())).thenReturn(List.of());

        DeliveryResult result = adapter.send(request());

        assertThat(result.skipped()).isTrue();
        assertThat(result.errorCode()).isEqualTo("NO_SUBSCRIPTION");
        verify(provider, never()).send(any());
    }

    private static PushSubscription subscription(String device) {
        return new PushSubscription(
                null,
                "https://push.example.test/" + device,
                "hash-" + device,
                "p256dh-" + device,
                "auth-" + device);
    }

    private static NotificationDeliveryRequest request() {
        UUID courseworkId = UUID.randomUUID();
        return new NotificationDeliveryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student@example.com",
                "+256700123456",
                "Matthew",
                NotificationChannel.PUSH,
                NotificationType.COURSEWORK_PUBLISHED,
                new NotificationMessage(
                        NotificationType.COURSEWORK_PUBLISHED,
                        "Coursework posted",
                        "A new coursework item is ready",
                        "A new coursework item is ready",
                        "View coursework",
                        "/coursework/" + courseworkId,
                        courseworkId,
                        null,
                        null,
                        null,
                        Map.of()));
    }
}
