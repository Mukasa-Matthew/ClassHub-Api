package com.classhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.common.api.ErrorCodes;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationRepository;
import com.classhub.notification.NotificationType;
import com.classhub.notification.delivery.NotificationMessageResolver;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, PasswordRecoveryIntegrationTest.ClockConfig.class})
@Transactional
class PasswordRecoveryIntegrationTest {

    private static final String OLD_PASSWORD = "OldPassword1!";
    private static final String NEW_PASSWORD = "NewPassword2!";
    private static final String EMAIL = "recovery.user@example.com";
    private static final Instant START = Instant.parse("2026-09-02T08:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private PasswordResetChallengeRepository challengeRepository;
    @Autowired private PasswordResetSecretFactory secretFactory;
    @Autowired private PasswordResetRateLimiter resetRateLimiter;
    @Autowired private LoginRateLimiter loginRateLimiter;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationDeliveryRepository deliveryRepository;
    @Autowired private NotificationMessageResolver messageResolver;
    @Autowired private MutableClock clock;

    private User user;

    @BeforeEach
    void setUp() {
        clock.set(START);
        resetRateLimiter.reset();
        loginRateLimiter.reset();
        user = userService.create(new CreateUserCommand(
                "Recovery", "User", EMAIL, "+256700123456", OLD_PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true, "RESET-" + UUID.randomUUID()));
    }

    @Test
    void validRequestQueuesSameOtpForVerifiedEmailAndWhatsappWithoutPersistingPlaintext() throws Exception {
        forgot(EMAIL, "10.0.0.1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.message").value(
                        "If an account matches those details, verification instructions have been sent."));

        PasswordResetChallenge challenge = latest();
        String otp = otp(challenge);
        assertThat(challenge.getOtpHash()).hasSize(64).isNotEqualTo(otp);
        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(note -> {
                    assertThat(note.getType()).isEqualTo(NotificationType.PASSWORD_RESET_OTP);
                    assertThat(note.getMessage()).doesNotContain(otp);
                });
        assertThat(deliveryRepository.findAll()).hasSize(2);
        assertThat(deliveryRepository.findAll())
                .extracting(delivery -> messageResolver.fromDelivery(delivery).body())
                .containsOnly("Your ClassHub verification code is " + otp
                        + ". It expires in 10 minutes. Do not share this code. "
                        + "If you did not request a password reset, ignore this message.");
    }

    @Test
    void unknownUserGetsExactlyTheSameGenericResponseAndNoChallenge() throws Exception {
        forgot("unknown@example.com", "10.0.0.2")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.message").value(
                        "If an account matches those details, verification instructions have been sent."));
        assertThat(challengeRepository.findAll()).isEmpty();
    }

    @Test
    void correctOtpIssuesHashedSinglePurposeAuthorizationAndOtpCannotBeReused() throws Exception {
        forgot(EMAIL, "10.0.0.3");
        PasswordResetChallenge challenge = latest();
        String token = verify(EMAIL, otp(challenge)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String resetToken = com.jayway.jsonpath.JsonPath.read(token, "$.data.resetToken");

        PasswordResetChallenge verified = challengeRepository.findById(challenge.getId()).orElseThrow();
        assertThat(verified.getResetTokenHash()).hasSize(64).isNotEqualTo(resetToken);
        assertThat(verified.getOtpVerifiedAt()).isNotNull();
        verify(EMAIL, otp(challenge)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_PASSWORD_RESET_OTP));
    }

    @Test
    void wrongOtpIsRejectedAndMaximumAttemptsLocksChallenge() throws Exception {
        forgot(EMAIL, "10.0.0.4");
        PasswordResetChallenge challenge = latest();
        for (int attempt = 0; attempt < 5; attempt++) {
            verify(EMAIL, "000000").andExpect(status().isBadRequest());
        }
        assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getFailedAttempts()).isEqualTo(5);
        verify(EMAIL, otp(challenge)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_PASSWORD_RESET_OTP));
    }

    @Test
    void expiredOtpIsRejected() throws Exception {
        forgot(EMAIL, "10.0.0.5");
        PasswordResetChallenge challenge = latest();
        clock.advance(Duration.ofMinutes(10));
        verify(EMAIL, otp(challenge)).andExpect(status().isBadRequest());
    }

    @Test
    void resendHonorsCooldownThenSupersedesPriorChallenge() throws Exception {
        forgot(EMAIL, "10.0.0.6");
        PasswordResetChallenge first = latest();
        forgot(EMAIL, "10.0.0.6");
        assertThat(challengeRepository.findAll()).hasSize(1);

        clock.advance(Duration.ofSeconds(61));
        forgot(EMAIL, "10.0.0.6");
        assertThat(challengeRepository.findAll()).hasSize(2);
        assertThat(challengeRepository.findById(first.getId()).orElseThrow().getSupersededAt()).isNotNull();
        verify(EMAIL, otp(first)).andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRequestsAreRateLimitedForKnownAndUnknownIdentifiers() throws Exception {
        for (int request = 0; request < 5; request++) {
            forgot("unknown@example.com", "10.0.0.7").andExpect(status().isAccepted());
        }
        forgot("unknown@example.com", "10.0.0.7").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.PASSWORD_RESET_RATE_LIMITED));
    }

    @Test
    void resetTokenExpires() throws Exception {
        String token = issueResetToken("10.0.0.8");
        clock.advance(Duration.ofMinutes(10));
        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_PASSWORD_RESET_TOKEN));
    }

    @Test
    void successfulResetChangesPasswordInvalidatesSessionNotifiesAndCannotBeReused() throws Exception {
        MockHttpSession existingSession = login(OLD_PASSWORD).getRequest().getSession(false) instanceof MockHttpSession session
                ? session : null;
        assertThat(existingSession).isNotNull();
        String token = issueResetToken("10.0.0.9");

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(existingSession))
                .andExpect(status().isUnauthorized());
        assertThat(loginStatus(OLD_PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(NEW_PASSWORD)).isEqualTo(200);
        reset(token, "AnotherPassword3!").andExpect(status().isBadRequest());
        assertThat(notificationRepository.findAll().stream()
                .filter(note -> note.getType() == NotificationType.PASSWORD_CHANGED)).hasSize(1);
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String identifier, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf()).header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\"}".formatted(identifier)));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String identifier, String otp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password/verify")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"otp\":\"%s\"}".formatted(identifier, otp)));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resetToken\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password)));
    }

    private String issueResetToken(String ip) throws Exception {
        forgot(EMAIL, ip).andExpect(status().isAccepted());
        PasswordResetChallenge challenge = latest();
        MvcResult result = verify(EMAIL, otp(challenge)).andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.resetToken");
    }

    private MvcResult login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, password)))
                .andReturn();
    }

    private int loginStatus(String password) throws Exception { return login(password).getResponse().getStatus(); }

    private PasswordResetChallenge latest() {
        return challengeRepository.findFirstByUserIdAndSupersededAtIsNullOrderByRequestedAtDesc(user.getId())
                .orElseThrow();
    }

    private String otp(PasswordResetChallenge challenge) {
        return secretFactory.otp(challenge.getId(), challenge.getRequestedAt());
    }

    static class MutableClock extends Clock {
        private Instant instant = START;
        void set(Instant value) { instant = value; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() { return new MutableClock(); }
    }
}
