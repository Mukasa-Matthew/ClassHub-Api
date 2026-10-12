package com.classhub.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class AnnouncementIntegrationTest {

    private static final String PASSWORD = "AnnouncePass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentSession;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        userService.create(new CreateUserCommand(
                "Super", "Admin", "ann.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "ann.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "ann.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("ann.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("ann.student@example.com"));

        adminSession = login("ann.admin@example.com");
        classRepSession = login("ann.rep@example.com");
        studentSession = login("ann.student@example.com");
    }

    @Test
    void createUpdatePublishArchiveAndVisibility() throws Exception {
        mockMvc.perform(post("/api/v1/announcements")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Room Change","content":"Cyber Security lecture moved to Room B12."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedAt").isEmpty());

        mockMvc.perform(post("/api/v1/announcements")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Admin Note","content":"Admin draft announcement.","classId":"%s"}
                                """.formatted(defaultClass.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/announcements")
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nope","content":"Students cannot create."}
                                """))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/announcements")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Deadline Update","content":"Scientific Writing deadline extended."}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json(created, "$.data.id"));

        mockMvc.perform(patch("/api/v1/announcements/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Deadline Extended","content":"Scientific Writing coursework deadline has been extended."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Deadline Extended"));

        mockMvc.perform(patch("/api/v1/announcements/{id}", id)
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/announcements/{id}", id).session(studentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.ANNOUNCEMENT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/announcements/{id}/archive", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ANNOUNCEMENT_STATE));

        mockMvc.perform(post("/api/v1/announcements/{id}/publish", id)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

        mockMvc.perform(patch("/api/v1/announcements/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cannot edit published\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ANNOUNCEMENT_STATE));

        mockMvc.perform(get("/api/v1/announcements/{id}", id).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/announcements").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='%s')]".formatted(id)).isNotEmpty());

        mockMvc.perform(post("/api/v1/announcements/{id}/archive", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/announcements/{id}", id).session(studentSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/announcements").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='%s')]".formatted(id)).isEmpty());

        mockMvc.perform(post("/api/v1/announcements/{id}/publish", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/announcements/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cannot edit archived\"}"))
                .andExpect(status().isConflict());

        assertThat(id).isNotNull();
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private static String json(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path).toString();
    }
}
