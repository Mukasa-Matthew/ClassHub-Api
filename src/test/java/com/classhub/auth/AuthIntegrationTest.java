package com.classhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
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
class AuthIntegrationTest {

    private static final String PASSWORD = "SecurePassw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private User activeStudent;
    private AcademicClass defaultClass;

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();

        activeStudent = userService.create(new CreateUserCommand(
                "Active",
                "Student",
                "active.student@example.com",
                null,
                PASSWORD,
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateStudent(defaultClass, activeStudent);
    }

    @Test
    void validLoginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Active.Student@Example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("active.student@example.com"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordFailsWithGenericAuthError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"active.student@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));
    }

    @Test
    void unknownEmailFailsWithSameGenericAuthError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));
    }

    @Test
    void suspendedUserCannotAuthenticate() throws Exception {
        userService.create(new CreateUserCommand(
                "Suspended",
                "User",
                "suspended.auth@example.com",
                null,
                PASSWORD,
                UserRole.STUDENT,
                UserStatus.SUSPENDED,
                false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"suspended.auth@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));
    }

    @Test
    void disabledUserCannotAuthenticate() throws Exception {
        userService.create(new CreateUserCommand(
                "Disabled",
                "User",
                "disabled.auth@example.com",
                null,
                PASSWORD,
                UserRole.STUDENT,
                UserStatus.DISABLED,
                false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"disabled.auth@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));
    }

    @Test
    void meReturnsAuthenticatedUserWithoutPasswordHash() throws Exception {
        MockHttpSession session = loginSession("active.student@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(activeStudent.getId().toString()))
                .andExpect(jsonPath("$.data.firstName").value("Active"))
                .andExpect(jsonPath("$.data.lastName").value("Student"))
                .andExpect(jsonPath("$.data.email").value("active.student@example.com"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void logoutInvalidatesAuthentication() throws Exception {
        MockHttpSession session = loginSession("active.student@example.com", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.UNAUTHENTICATED));
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.UNAUTHENTICATED));
    }

    @Test
    void healthAndReadyRemainPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    private MockHttpSession loginSession(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }
}
