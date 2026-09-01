package com.classhub.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class AdminUserIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPassw0rd!";
    private static final String USER_PASSWORD = "UserPassw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    private User admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        admin = userService.create(new CreateUserCommand(
                "Super",
                "Admin",
                "super.admin@example.com",
                null,
                ADMIN_PASSWORD,
                UserRole.SUPER_ADMIN,
                UserStatus.ACTIVE,
                true));
        adminSession = login("super.admin@example.com", ADMIN_PASSWORD);
    }

    @Test
    void superAdminCanCreateStudentAndClassRepWithoutExposingPasswordHash() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Stu",
                                  "lastName":"Dent",
                                  "email":"New.Student@Example.com",
                                  "phoneNumber":"+256700111222",
                                  "password":"%s",
                                  "role":"STUDENT"
                                }
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new.student@example.com"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Class",
                                  "lastName":"Rep",
                                  "email":"class.rep@example.com",
                                  "password":"%s",
                                  "role":"CLASS_REP"
                                }
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("CLASS_REP"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User student = userRepository.findByEmail("new.student@example.com").orElseThrow();
        String storedHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?",
                String.class,
                student.getId());
        assertThat(storedHash).isNotEqualTo(USER_PASSWORD);
        assertThat(storedHash).startsWith("$argon2");
        assertThat(passwordEncoder.matches(USER_PASSWORD, storedHash)).isTrue();
    }

    @Test
    void superAdminCannotCreateSuperAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Another",
                                  "lastName":"Admin",
                                  "email":"another.admin@example.com",
                                  "password":"%s",
                                  "role":"SUPER_ADMIN"
                                }
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ROLE_CHANGE));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        createManagedUser("dup@example.com", UserRole.STUDENT);

        mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Dup",
                                  "lastName":"Two",
                                  "email":"DUP@example.com",
                                  "password":"%s",
                                  "role":"STUDENT"
                                }
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.USER_ALREADY_EXISTS));
    }

    @Test
    void listUsersSupportsPaginationAndFilters() throws Exception {
        createManagedUser("student.one@example.com", UserRole.STUDENT);
        createManagedUser("student.two@example.com", UserRole.STUDENT);
        createManagedUser("rep.one@example.com", UserRole.CLASS_REP);

        mockMvc.perform(get("/api/v1/admin/users")
                        .session(adminSession)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(4))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/users")
                        .session(adminSession)
                        .param("role", "CLASS_REP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("rep.one@example.com"));

        User suspended = createManagedUser("suspended.filter@example.com", UserRole.STUDENT);
        adminUserStatus(suspended.getId(), "SUSPENDED");

        mockMvc.perform(get("/api/v1/admin/users")
                        .session(adminSession)
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("suspended.filter@example.com"));
    }

    @Test
    void getUserByIdAndUnknownUser() throws Exception {
        User student = createManagedUser("get.me@example.com", UserRole.STUDENT);

        mockMvc.perform(get("/api/v1/admin/users/{id}", student.getId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("get.me@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/users/{id}", UUID.randomUUID()).session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.USER_NOT_FOUND));
    }

    @Test
    void roleChangesBetweenStudentAndClassRepButNotToSuperAdmin() throws Exception {
        User student = createManagedUser("role.change@example.com", UserRole.STUDENT);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", student.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CLASS_REP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CLASS_REP"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", student.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", student.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ROLE_CHANGE));
    }

    @Test
    void statusChangesAffectLoginAndSelfDisableIsBlocked() throws Exception {
        User student = createManagedUser("status.change@example.com", UserRole.STUDENT);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", student.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"status.change@example.com","password":"%s"}
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", student.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"status.change@example.com","password":"%s"}
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", student.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"status.change@example.com","password":"%s"}
                                """.formatted(USER_PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", admin.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.CANNOT_MODIFY_SELF));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", admin.getId())
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.CANNOT_MODIFY_SELF));
    }

    @Test
    void classRepAndStudentGetForbiddenAndUnauthenticatedGets401() throws Exception {
        createManagedUser("rep.authz@example.com", UserRole.CLASS_REP);
        createManagedUser("student.authz@example.com", UserRole.STUDENT);
        MockHttpSession repSession = login("rep.authz@example.com", USER_PASSWORD);
        MockHttpSession studentSession = login("student.authz@example.com", USER_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/users").session(repSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.FORBIDDEN));

        mockMvc.perform(get("/api/v1/admin/users").session(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.FORBIDDEN));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.UNAUTHENTICATED));
    }

    private User createManagedUser(String email, UserRole role) {
        return userService.create(new CreateUserCommand(
                "Managed",
                "User",
                email,
                null,
                USER_PASSWORD,
                role,
                UserStatus.ACTIVE,
                false));
    }

    private void adminUserStatus(UUID id, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String email, String password) throws Exception {
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
