package com.classhub.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationType;
import com.classhub.notification.config.NotificationProperties;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationChannelTemplateServiceTest {

    private final NotificationProperties properties = properties();
    private final NotificationChannelTemplateService templates =
            new NotificationChannelTemplateService(properties);

    @Test
    void passwordResetTemplatesContainOtpSafetyLanguageWithoutInternalSecrets() {
        NotificationDeliveryRequest request = request(
                NotificationType.PASSWORD_RESET_OTP,
                "ClassHub password reset code",
                "Your ClassHub verification code is 123456. It expires in 10 minutes. "
                        + "Do not share this code. If you did not request a password reset, ignore this message.",
                "/forgot-password");

        NotificationChannelTemplateService.EmailContent email = templates.email(request);
        String whatsapp = templates.whatsapp(request);

        assertThat(email.subject()).isEqualTo("ClassHub password reset code");
        assertThat(email.plainText())
                .contains("Hello Matthew", "123456", "10 minutes", "never share", "did not request")
                .doesNotContain("resetToken", "token hash", "password=");
        assertThat(whatsapp)
                .contains("ClassHub", "Hi Matthew", "123456", "10 minutes", "Do not share", "did not request")
                .doesNotContain("<html", "resetToken", "token hash");
    }

    @Test
    void setupAndAcademicMessagesUseConfiguredLinksAndChannelAppropriateCopy() {
        NotificationDeliveryRequest setup = request(
                NotificationType.ACCOUNT_SETUP,
                "Complete your ClassHub account setup",
                "Your ClassHub account has been created. Complete your account setup using this secure link.",
                "/complete-account?token=single-use-value");
        NotificationDeliveryRequest coursework = request(
                NotificationType.COURSEWORK_PUBLISHED,
                "New coursework: Research Proposal",
                "BSIT 3104 • ASSIGNMENT • Due Fri 11 Sep 2026, 17:00",
                "/coursework/coursework-reference");

        assertThat(templates.email(setup).html())
                .contains("https://classhub.example/complete-account?token=single-use-value")
                .doesNotContain("localhost", "temporary password");
        assertThat(templates.whatsapp(setup))
                .contains("Complete account setup:", "https://classhub.example/complete-account")
                .doesNotContain("<html");
        assertThat(templates.email(coursework).plainText())
                .contains("New coursework", "BSIT 3104", "Open in ClassHub")
                .doesNotContain("null", "localhost");
        assertThat(templates.whatsapp(coursework))
                .contains("ClassHub", "Research Proposal", "BSIT 3104", "View coursework")
                .doesNotContain("<html", "null", "localhost");
    }

    private static NotificationDeliveryRequest request(
            NotificationType type, String title, String body, String actionPath) {
        NotificationMessage message = new NotificationMessage(
                type, title, body, body, "Open in ClassHub", actionPath,
                null, null, null, null, Map.of());
        return new NotificationDeliveryRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "student@example.com", "+256700123456", "Matthew",
                NotificationChannel.EMAIL, type, message);
    }

    private static NotificationProperties properties() {
        NotificationProperties properties = new NotificationProperties();
        properties.setWebBaseUrl("https://classhub.example");
        return properties;
    }
}
