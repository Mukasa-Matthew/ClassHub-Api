package com.classhub.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class PushSubscriptionIntegrationTest {

    private static final String PASSWORD = "PushSubscription1!";
    private static final String ENDPOINT = "https://push.example.test/send/device-one";
    private static final String P256DH = key(65, 1);
    private static final String AUTH = key(16, 2);
    private static final String UPDATED_P256DH = key(65, 3);
    private static final String UPDATED_AUTH = key(16, 4);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private PushSubscriptionRepository subscriptionRepository;
    @Autowired private LoginRateLimiter loginRateLimiter;

    private MockHttpSession userASession;
    private MockHttpSession userBSession;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        createUser("push.a@example.com", "PUSH-A");
        createUser("push.b@example.com", "PUSH-B");
        userASession = login("push.a@example.com");
        userBSession = login("push.b@example.com");
    }

    @Test
    void authenticatedUserRegistersOwnSubscription() throws Exception {
        register(userASession, ENDPOINT, P256DH, AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriptionCount").value(1));

        assertThat(subscriptionRepository.findAll()).singleElement().satisfies(subscription -> {
            assertThat(subscription.getEndpoint()).isEqualTo(ENDPOINT);
            assertThat(subscription.getP256dhKey()).isEqualTo(P256DH);
            assertThat(subscription.getAuthKey()).isEqualTo(AUTH);
        });
    }

    @Test
    void registeringSameEndpointAgainUpdatesKeysWithoutDuplicateRow() throws Exception {
        register(userASession, ENDPOINT, P256DH, AUTH).andExpect(status().isOk());
        UUID id = subscriptionRepository.findAll().getFirst().getId();

        register(userASession, ENDPOINT, UPDATED_P256DH, UPDATED_AUTH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptionCount").value(1));

        assertThat(subscriptionRepository.findAll()).singleElement().satisfies(subscription -> {
            assertThat(subscription.getId()).isEqualTo(id);
            assertThat(subscription.getP256dhKey()).isEqualTo(UPDATED_P256DH);
            assertThat(subscription.getAuthKey()).isEqualTo(UPDATED_AUTH);
        });
    }

    @Test
    void endpointCannotBeClaimedByAnotherUser() throws Exception {
        register(userASession, ENDPOINT, P256DH, AUTH).andExpect(status().isOk());

        register(userBSession, ENDPOINT, UPDATED_P256DH, UPDATED_AUTH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.PUSH_SUBSCRIPTION_CONFLICT));

        assertThat(subscriptionRepository.findAll()).singleElement()
                .satisfies(subscription -> assertThat(subscription.getP256dhKey()).isEqualTo(P256DH));
    }

    @Test
    void ownerCanRemoveSubscriptionAndDeleteIsIdempotent() throws Exception {
        register(userASession, ENDPOINT, P256DH, AUTH).andExpect(status().isOk());

        remove(userASession, ENDPOINT).andExpect(status().isNoContent());
        remove(userASession, ENDPOINT).andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    @Test
    void oneUserCannotInspectOrDeleteAnotherUsersSubscription() throws Exception {
        register(userASession, ENDPOINT, P256DH, AUTH).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/push-subscriptions/status")
                        .session(userBSession)
                        .queryParam("endpoint", ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriptionCount").value(0));
        remove(userBSession, ENDPOINT).andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findAll()).hasSize(1);
    }

    @Test
    void statusSupportsCurrentDeviceAndAnyDeviceChecks() throws Exception {
        register(userASession, ENDPOINT, P256DH, AUTH).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/push-subscriptions/status").session(userASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriptionCount").value(1));
        mockMvc.perform(get("/api/v1/me/push-subscriptions/status")
                        .session(userASession)
                        .queryParam("endpoint", "https://push.example.test/send/other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(false));
    }

    @Test
    void malformedEndpointAndKeysAreRejected() throws Exception {
        register(userASession, "http://push.example.test/device", P256DH, AUTH)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_PUSH_SUBSCRIPTION));
        register(userASession, ENDPOINT, "not-base64!", AUTH)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_PUSH_SUBSCRIPTION));
        register(userASession, ENDPOINT, P256DH, key(15, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_PUSH_SUBSCRIPTION));
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    @Test
    void endpointsRequireAuthenticationAndCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/me/push-subscriptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ENDPOINT, P256DH, AUTH)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/push-subscriptions")
                        .session(userASession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ENDPOINT, P256DH, AUTH)))
                .andExpect(status().isForbidden());
    }

    private void createUser(String email, String registrationNumber) {
        userService.create(new CreateUserCommand(
                "Push", "User", email, null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true, registrationNumber));
    }

    private org.springframework.test.web.servlet.ResultActions register(
            MockHttpSession session, String endpoint, String p256dh, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/me/push-subscriptions")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(endpoint, p256dh, auth)));
    }

    private org.springframework.test.web.servlet.ResultActions remove(
            MockHttpSession session, String endpoint) throws Exception {
        return mockMvc.perform(delete("/api/v1/me/push-subscriptions")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\"}".formatted(endpoint)));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String body(String endpoint, String p256dh, String auth) {
        return """
                {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"}}
                """.formatted(endpoint, p256dh, auth);
    }

    private static String key(int length, int seed) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (seed + index);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
