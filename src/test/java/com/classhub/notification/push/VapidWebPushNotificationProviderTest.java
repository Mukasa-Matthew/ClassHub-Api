package com.classhub.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.DeliveryResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.ObjectMapper;

class VapidWebPushNotificationProviderTest {

    @Mock private WebPushTransport transport;

    private NotificationProperties properties;
    private VapidWebPushNotificationProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new NotificationProperties();
        provider = new VapidWebPushNotificationProvider(properties, transport, new ObjectMapper());
    }

    @Test
    void disabledProviderDoesNotUseTransport() throws Exception {
        DeliveryResult result = provider.send(command());

        assertThat(result.skipped()).isTrue();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_DISABLED");
        verify(transport, never()).send(any());
    }

    @Test
    void missingVapidConfigurationFailsSafelyWithoutUsingTransport() throws Exception {
        properties.getPush().setEnabled(true);

        DeliveryResult result = provider.send(command());

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_NOT_CONFIGURED");
        verify(transport, never()).send(any());
    }

    @Test
    void successfulSendUsesVapidConfigurationAndMinimalJsonPayload() throws Exception {
        configure();
        when(transport.send(any())).thenReturn(new WebPushTransportResponse(201, "push-message-1"));

        DeliveryResult result = provider.send(command());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("push-message-1");
        ArgumentCaptor<WebPushTransportRequest> captor =
                ArgumentCaptor.forClass(WebPushTransportRequest.class);
        verify(transport).send(captor.capture());
        WebPushTransportRequest sent = captor.getValue();
        assertThat(sent.vapidPublicKey()).isEqualTo("public-key");
        assertThat(sent.vapidPrivateKey()).isEqualTo("private-key");
        assertThat(sent.vapidSubject()).isEqualTo("mailto:push@classhub.test");
        assertThat(sent.payload()).contains(
                "\"title\":\"ClassHub\"",
                "\"body\":\"Coursework is ready\"",
                "\"url\":\"https://app.classhub.test/coursework/123\"",
                "\"notificationType\":\"COURSEWORK_PUBLISHED\"",
                "\"referenceId\":\"123\"");
        assertThat(sent.payload()).doesNotContain("private-key", "p256dh-value", "auth-value");
    }

    @Test
    void mapsNotFoundAndGoneToExpiredSubscription() throws Exception {
        configure();
        when(transport.send(any()))
                .thenReturn(new WebPushTransportResponse(404, null))
                .thenReturn(new WebPushTransportResponse(410, null));

        assertThat(provider.send(command()).errorCode())
                .isEqualTo(VapidWebPushNotificationProvider.EXPIRED_SUBSCRIPTION);
        assertThat(provider.send(command()).errorCode())
                .isEqualTo(VapidWebPushNotificationProvider.EXPIRED_SUBSCRIPTION);
    }

    @Test
    void transientTransportAndProviderFailuresRemainRetryable() throws Exception {
        configure();
        when(transport.send(any()))
                .thenReturn(new WebPushTransportResponse(503, null))
                .thenThrow(new WebPushTransportException("network", null));

        DeliveryResult unavailable = provider.send(command());
        DeliveryResult network = provider.send(command());

        assertThat(unavailable.skipped()).isFalse();
        assertThat(unavailable.errorCode()).isEqualTo("WEB_PUSH_UNAVAILABLE");
        assertThat(network.skipped()).isFalse();
        assertThat(network.errorCode()).isEqualTo("WEB_PUSH_UNAVAILABLE");
    }

    private void configure() {
        properties.getPush().setEnabled(true);
        properties.getPush().setVapidPublicKey("public-key");
        properties.getPush().setVapidPrivateKey("private-key");
        properties.getPush().setSubject("mailto:push@classhub.test");
    }

    private static WebPushSendCommand command() {
        return new WebPushSendCommand(
                UUID.randomUUID(),
                "https://push.example.test/device",
                "p256dh-value",
                "auth-value",
                "ClassHub",
                "Coursework is ready",
                "https://app.classhub.test/coursework/123",
                "COURSEWORK_PUBLISHED",
                "123");
    }
}
