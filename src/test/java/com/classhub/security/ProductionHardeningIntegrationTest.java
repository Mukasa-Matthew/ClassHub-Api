package com.classhub.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import com.classhub.web.RequestIdFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class ProductionHardeningIntegrationTest {

    private static final String PASSWORD = "HardenPassw0rd!";
    private static final String ALLOWED_ORIGIN = "http://127.0.0.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession studentSession;
    private MockHttpSession classRepSession;
    private User studentA;
    private User studentB;
    private CourseUnit courseUnit;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        userService.create(new CreateUserCommand(
                "Super", "Admin", "harden.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "harden.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        studentA = userService.create(new CreateUserCommand(
                "Stu", "A", "harden.student.a@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        studentB = userService.create(new CreateUserCommand(
                "Stu", "B", "harden.student.b@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("harden.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, studentA);
        membershipTestSupport.activateStudent(defaultClass, studentB);

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-996001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));

        adminSession = login("harden.admin@example.com");
        classRepSession = login("harden.rep@example.com");
        studentSession = login("harden.student.a@example.com");
    }

    @Test
    void publicEndpointsRemainAccessibleAndReadyChecksDatabase() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(header().exists(RequestIdFilter.HEADER));

        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void protectedRoutesEnforceAuthRolesAndOwnership() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.UNAUTHENTICATED))
                .andExpect(jsonPath("$.error.path").value("/api/v1/auth/me"))
                .andExpect(jsonPath("$.error.timestamp").exists());

        mockMvc.perform(get("/api/v1/admin/users").session(studentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.FORBIDDEN));

        mockMvc.perform(get("/api/v1/admin/audit-logs").session(classRepSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/student").session(adminSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notes").session(classRepSession))
                .andExpect(status().isOk());

        MvcResult note = mockMvc.perform(post("/api/v1/notes")
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"Private",
                                  "rawContent":"secret note body for ownership check"
                                }
                                """.formatted(courseUnit.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID noteId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        note.getResponse().getContentAsString(), "$.data.id")
                .toString());

        MockHttpSession otherStudent = login("harden.student.b@example.com");
        mockMvc.perform(get("/api/v1/notes/{id}", noteId).session(otherStudent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.LECTURE_NOTE_NOT_FOUND));

        assertThat(studentA.getId()).isNotEqualTo(studentB.getId());
    }

    @Test
    void csrfIsRequiredForMutations() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"No CSRF Unit\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"With CSRF Unit\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void corsAllowsConfiguredOriginAndRejectsOthers() throws Exception {
        mockMvc.perform(options("/api/v1/auth/me")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void securityHeadersAndRequestIdArePresent() throws Exception {
        mockMvc.perform(get("/health").header(RequestIdFilter.HEADER, "client-req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "client-req-123"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));

        mockMvc.perform(get("/health").header(RequestIdFilter.HEADER, "bad id with spaces!!!"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, not(containsString(" "))));
    }

    @Test
    void malformedAndValidationErrorsAreStandardizedWithoutLeaks() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}", "not-a-uuid").session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.error.path").value("/api/v1/admin/users/not-a-uuid"))
                .andExpect(jsonPath("$.error.message").value("Invalid request parameter"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.error.fieldErrors").isArray())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").exists());

        String unknown = mockMvc.perform(get("/api/v1/this-route-does-not-exist").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.NOT_FOUND))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(unknown.toLowerCase()).doesNotContain("exception");
        assertThat(unknown.toLowerCase()).doesNotContain("sql");
        assertThat(unknown).doesNotContain("classhub-test-storage");
    }

    @Test
    void oversizedUploadReturnsCleanErrorAndExecutableNamesRejected() throws Exception {
        Instant due = Instant.now().plus(5, ChronoUnit.DAYS);
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"Upload Harden",
                                  "description":"desc",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(courseUnit.getId(), due)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID courseworkId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        created.getResponse().getContentAsString(), "$.data.id")
                .toString());

        byte[] oversized = new byte[1024 * 1024 + 8];
        MockMultipartFile big = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", oversized);
        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", courseworkId)
                        .file(big)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.ATTACHMENT_TOO_LARGE))
                .andExpect(jsonPath("$.error.message").value("Attachment exceeds the maximum allowed size"));

        MockMultipartFile exe = new MockMultipartFile(
                "file",
                "notes.pdf.exe",
                "application/pdf",
                "x".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", courseworkId)
                        .file(exe)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ATTACHMENT));
    }

    @Test
    void suspendedAndDisabledUsersCannotAuthenticate() throws Exception {
        userService.create(new CreateUserCommand(
                "Sus", "User", "harden.suspended@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.SUSPENDED, true));
        userService.create(new CreateUserCommand(
                "Dis", "User", "harden.disabled@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.DISABLED, true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"harden.suspended@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"harden.disabled@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AUTHENTICATION_FAILED));
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
}
