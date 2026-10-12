package com.classhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.academicclass.AcademicClassRepository;
import com.classhub.academicclass.ClassMembership;
import com.classhub.academicclass.ClassMembershipRepository;
import com.classhub.academicclass.MembershipRole;
import com.classhub.academicclass.MembershipStatus;
import com.classhub.common.api.ErrorCodes;
import com.classhub.notification.NotificationChannel;
import com.classhub.notification.Notification;
import com.classhub.notification.NotificationDelivery;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationRepository;
import com.classhub.notification.NotificationType;
import com.classhub.notification.delivery.NotificationMessageResolver;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "classhub.auth.class-rep-setup.signing-key=integration-test-signing-key-with-sufficient-entropy",
        "classhub.auth.class-rep-setup.token-ttl=PT24H"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class ClassRepOnboardingIntegrationTest {

    private static final String EMAIL = "new.rep@example.com";
    private static final String PHONE = "+256 755 032 436";
    private static final String PASSWORD = "SecureSetupPass1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AcademicClassRepository classRepository;
    @Autowired private ClassMembershipRepository membershipRepository;
    @Autowired private ClassRepAccountSetupRepository setupRepository;
    @Autowired private ClassRepSetupTokenFactory tokenFactory;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationDeliveryRepository deliveryRepository;
    @Autowired private NotificationMessageResolver messageResolver;
    @Autowired private ClassRepOnboardingRateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void resetRateLimit() {
        rateLimiter.reset();
    }

    @Test
    void registrationCreatesPendingAccountHashedTokenAndEmailWhatsappOutbox() throws Exception {
        register(EMAIL, PHONE).andExpect(status().isAccepted());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_SETUP);
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.isEmailVerified()).isFalse();

        ClassRepAccountSetup setup = onlySetup();
        String token = tokenFor(setup);
        assertThat(setup.getTokenHash()).isEqualTo(tokenFactory.hash(token)).doesNotContain(token);

        var notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.ACCOUNT_SETUP);
        assertThat(notifications.getFirst().getMessage()).doesNotContain(token);

        List<NotificationDelivery> deliveries = deliveryRepository.findAll();
        assertThat(deliveries).extracting(NotificationDelivery::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
        assertThat(deliveries).allSatisfy(delivery -> assertThat(
                        messageResolver.fromDelivery(delivery).actionPath())
                .isEqualTo("/complete-account?token=" + token));

        Integer persistedPlaintext = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select token_hash as value from class_rep_account_setups
                    union all select message from notifications
                    union all select occurrence_key from notifications
                ) persisted where value like '%' || ? || '%'
                """, Integer.class, token);
        assertThat(persistedPlaintext).isZero();
    }

    @Test
    void validTokenSetsPasswordCreatesClassMembershipAndCannotBeReused() throws Exception {
        register(EMAIL, PHONE).andExpect(status().isAccepted());
        String token = tokenFor(onlySetup());

        complete(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.programmeName").value("BSc Computer Science"))
                .andExpect(jsonPath("$.data.studyYear").value(3))
                .andExpect(jsonPath("$.data.semester").value(2))
                .andExpect(jsonPath("$.data.academicYear").value(2026))
                .andExpect(jsonPath("$.data.joinCode").isNotEmpty());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isNotBlank().doesNotContain(PASSWORD);
        assertThat(user.isEmailVerified()).isTrue();

        AcademicClass academicClass = classRepository.findAll().stream()
                .filter(candidate -> candidate.getProgrammeName().equals("BSc Computer Science"))
                .findFirst().orElseThrow();
        assertThat(academicClass.getName()).isEqualTo("BSc Computer Science Year 3");
        assertThat(academicClass.getJoinCode()).hasSize(6);
        ClassMembership membership = membershipRepository
                .findByAcademicClassIdAndUserId(academicClass.getId(), user.getId()).orElseThrow();
        assertThat(membership.getMembershipRole()).isEqualTo(MembershipRole.CLASS_REP);
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(onlySetup().getUsedAt()).isNotNull();
        Notification welcome = notificationRepository.findAll().stream()
                .filter(note -> note.getType() == NotificationType.ACCOUNT_SETUP_COMPLETED)
                .findFirst().orElseThrow();
        assertThat(welcome.getMessage())
                .contains("BSc Computer Science", "Year 3", "Semester 2", "Academic Year 2026")
                .doesNotContain(PASSWORD, token, "null");

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk());

        complete(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ACCOUNT_SETUP_TOKEN));
    }

    @Test
    void invalidAndExpiredTokensAreRejectedWithoutActivatingAccount() throws Exception {
        register(EMAIL, PHONE).andExpect(status().isAccepted());
        ClassRepAccountSetup setup = onlySetup();

        complete("not-a-valid-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ACCOUNT_SETUP_TOKEN));

        jdbcTemplate.update(
                "update class_rep_account_setups set expires_at = ? where id = ?",
                java.sql.Timestamp.from(setup.getIssuedAt().plusMillis(1)),
                setup.getId());
        entityManager.clear();
        ClassRepAccountSetup expired = setupRepository.findById(setup.getId()).orElseThrow();
        complete(tokenFor(expired))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.EXPIRED_ACCOUNT_SETUP_TOKEN));
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_SETUP);
    }

    @Test
    void duplicateEmailOrPhoneRegistrationIsRejected() throws Exception {
        register(EMAIL, PHONE).andExpect(status().isAccepted());
        register(EMAIL, "+256700000001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.USER_ALREADY_EXISTS));
        register("another.rep@example.com", PHONE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.USER_ALREADY_EXISTS));
    }

    @Test
    void reissueSupersedesOldTokenAndQueuesFreshSingleUseLink() throws Exception {
        register(EMAIL, PHONE).andExpect(status().isAccepted());
        ClassRepAccountSetup oldSetup = onlySetup();
        String oldToken = tokenFor(oldSetup);

        mockMvc.perform(post("/api/v1/auth/class-rep/setup-link/reissue").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"phoneNumber\":\"%s\"}".formatted(EMAIL, PHONE)))
                .andExpect(status().isAccepted());

        List<ClassRepAccountSetup> setups = setupRepository.findAll();
        assertThat(setups).hasSize(2);
        assertThat(setups.stream().filter(candidate -> candidate.getSupersededAt() != null)).hasSize(1);
        ClassRepAccountSetup fresh = setups.stream()
                .filter(candidate -> candidate.getSupersededAt() == null).findFirst().orElseThrow();
        assertThat(tokenFor(fresh)).isNotEqualTo(oldToken);
        complete(oldToken).andExpect(status().isBadRequest());
        complete(tokenFor(fresh)).andExpect(status().isOk());
    }

    @Test
    void setupLinkReissueIsRateLimited() throws Exception {
        register(EMAIL, PHONE).andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 5; attempt++) {
            reissue().andExpect(status().isAccepted());
        }
        reissue()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.ONBOARDING_RATE_LIMITED));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String phone) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/class-rep/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"New","lastName":"Representative","email":"%s","phoneNumber":"%s"}
                        """.formatted(email, phone)));
    }

    private org.springframework.test.web.servlet.ResultActions complete(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/class-rep/complete-account").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "token":"%s",
                          "password":"%s",
                          "programmeName":"  BSc Computer Science  ",
                          "studyYear":3,
                          "semester":2,
                          "academicYear":2026
                        }
                        """.formatted(token, PASSWORD)));
    }

    private org.springframework.test.web.servlet.ResultActions reissue() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/class-rep/setup-link/reissue").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"phoneNumber\":\"%s\"}".formatted(EMAIL, PHONE)));
    }

    private ClassRepAccountSetup onlySetup() {
        return setupRepository.findAll().getFirst();
    }

    private String tokenFor(ClassRepAccountSetup setup) {
        return tokenFactory.create(setup.getId(), setup.getIssuedAt());
    }
}
