package com.classhub.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationType;
import com.classhub.notification.config.NotificationProperties;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExternalNotificationDeliveryAdapterTest {

    @Test
    void brevoSendsTransactionalEmailWithConfiguredCredentials() {
        NotificationProperties properties = properties();
        properties.getEmail().setEnabled(true);
        properties.getEmail().setApiUrl("https://brevo.test/v3/smtp/email");
        properties.getEmail().setApiKey("test-brevo-key");
        properties.getEmail().setSenderName("ClassHub Team");
        properties.getEmail().setSenderEmail("notifications@classhub.test");
        properties.getEmail().setReplyToEmail("support@classhub.test");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-brevo-key"))
                .andExpect(jsonPath("$.sender.email").value("notifications@classhub.test"))
                .andExpect(jsonPath("$.to[0].email").value("student@example.com"))
                .andExpect(jsonPath("$.subject").value("Coursework posted"))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString(
                        "https://app.classhub.test/coursework/123")))
                .andRespond(withSuccess("{\"messageId\":\"brevo-message-1\"}", MediaType.APPLICATION_JSON));

        DeliveryResult result = new BrevoEmailNotificationDeliveryAdapter(
                properties, builder, new NotificationChannelTemplateService(properties)).send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("brevo-message-1");
        server.verify();
    }

    @Test
    void sopraSendQueuesConciseMessageWithNormalizedPhone() {
        NotificationProperties properties = properties();
        properties.getWhatsapp().setEnabled(true);
        properties.getWhatsapp().setBaseUrl("https://soprasend.test");
        properties.getWhatsapp().setApiKey("test-soprasend-key");
        properties.getWhatsapp().setDeviceId("device-123");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://soprasend.test/api/v1/messages/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-soprasend-key"))
                .andExpect(jsonPath("$.device_id").value("device-123"))
                .andExpect(jsonPath("$.to").value("256700123456"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(
                        "https://app.classhub.test/coursework/123")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message_id\":\"sopra-message-1\"}"));

        WhatsAppProvider provider = new SopraSendWhatsAppProvider(properties, builder);
        DeliveryResult result = new SopraSendWhatsAppNotificationDeliveryAdapter(
                        properties, provider, new NotificationChannelTemplateService(properties))
                .send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("sopra-message-1");
        server.verify();
    }

    @Test
    void disabledProvidersSkipWithoutCallingExternalApis() {
        NotificationProperties properties = properties();

        DeliveryResult email = new BrevoEmailNotificationDeliveryAdapter(
                        properties, RestClient.builder(), new NotificationChannelTemplateService(properties))
                .send(request());
        WhatsAppProvider provider = new SopraSendWhatsAppProvider(properties, RestClient.builder());
        DeliveryResult whatsapp = new SopraSendWhatsAppNotificationDeliveryAdapter(
                        properties, provider, new NotificationChannelTemplateService(properties))
                .send(request());

        assertThat(email.skipped()).isTrue();
        assertThat(whatsapp.skipped()).isTrue();
    }

    @Test
    void whatsappPhoneNormalizationUsesInternationalDigitsOnly() {
        assertThat(WhatsAppPhoneNormalizer.toProviderNumber("+256 700-123-456"))
                .isEqualTo("256700123456");
        assertThat(WhatsAppPhoneNormalizer.toProviderNumber("0700123456")).isEqualTo("256700123456");
        assertThat(WhatsAppPhoneNormalizer.toProviderNumber("700123456")).isEqualTo("256700123456");
        assertThat(WhatsAppPhoneNormalizer.toProviderNumber("123")).isNull();
    }

    private static NotificationProperties properties() {
        NotificationProperties properties = new NotificationProperties();
        properties.setWebBaseUrl("https://app.classhub.test");
        return properties;
    }

    private static NotificationDeliveryRequest request() {
        return new NotificationDeliveryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student@example.com",
                "+256 700-123-456",
                "Matthew",
                NotificationChannel.EMAIL,
                NotificationType.COURSEWORK_PUBLISHED,
                new NotificationMessage(
                        NotificationType.COURSEWORK_PUBLISHED,
                        "Coursework posted",
                        "A new task is ready",
                        "A new task is ready",
                        "Open in ClassHub",
                        "/coursework/123",
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        Map.of()));
    }
}
