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

        DeliveryResult result = new BrevoEmailNotificationDeliveryAdapter(properties, builder).send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("brevo-message-1");
        server.verify();
    }

    @Test
    void metaSendsApprovedTemplateWithNormalizedPhoneAndVariables() {
        NotificationProperties properties = properties();
        properties.getWhatsapp().setEnabled(true);
        properties.getWhatsapp().setGraphApiBaseUrl("https://graph.test");
        properties.getWhatsapp().setGraphApiVersion("v24.0");
        properties.getWhatsapp().setAccessToken("test-meta-token");
        properties.getWhatsapp().setPhoneNumberId("123456789");
        properties.getWhatsapp().setTemplateName("classhub_notification");
        properties.getWhatsapp().setTemplateLanguage("en");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.test/v24.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-meta-token"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("256700123456"))
                .andExpect(jsonPath("$.template.name").value("classhub_notification"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("Matthew"))
                .andExpect(jsonPath("$.template.components[0].parameters[3].text")
                        .value("https://app.classhub.test/coursework/123"))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.test-message-1\"}]}",
                        MediaType.APPLICATION_JSON));

        DeliveryResult result = new MetaWhatsAppNotificationDeliveryAdapter(properties, builder).send(request());

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("wamid.test-message-1");
        server.verify();
    }

    @Test
    void disabledProvidersSkipWithoutCallingExternalApis() {
        NotificationProperties properties = properties();

        DeliveryResult email = new BrevoEmailNotificationDeliveryAdapter(properties, RestClient.builder())
                .send(request());
        DeliveryResult whatsapp = new MetaWhatsAppNotificationDeliveryAdapter(properties, RestClient.builder())
                .send(request());

        assertThat(email.skipped()).isTrue();
        assertThat(whatsapp.skipped()).isTrue();
    }

    @Test
    void whatsappPhoneNormalizationUsesInternationalDigitsOnly() {
        assertThat(MetaWhatsAppNotificationDeliveryAdapter.normalizePhone("+256 700-123-456"))
                .isEqualTo("256700123456");
        assertThat(MetaWhatsAppNotificationDeliveryAdapter.normalizePhone("123"))
                .isNull();
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
