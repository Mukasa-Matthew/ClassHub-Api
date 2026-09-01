package com.classhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class AuditIntegrationTest {

    private static final String PASSWORD = "AuditPassw0rd!";
    private static final String SECRET_NOTE =
            "PRIVATE_LECTURE_NOTE_CONTENT_SHOULD_NEVER_APPEAR_IN_AUDIT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentSession;
    private User admin;
    private CourseUnit courseUnit;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        admin = userService.create(new CreateUserCommand(
                "Super", "Admin", "audit.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "audit.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "audit.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("audit.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("audit.student@example.com"));

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-993001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));

        adminSession = login("audit.admin@example.com");
        classRepSession = login("audit.rep@example.com");
        studentSession = login("audit.student@example.com");
    }

    @Test
    void mutatingActionsCreateTrustedAuditRecordsAndFailedActionsDoNot() throws Exception {
        long beforeFailed = auditLogRepository.count();
        mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Bad",
                                  "lastName":"Admin",
                                  "email":"bad.admin@example.com",
                                  "password":"%s",
                                  "role":"SUPER_ADMIN"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest());
        assertThat(auditLogRepository.count()).isEqualTo(beforeFailed);

        MvcResult createdUser = mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"New",
                                  "lastName":"Student",
                                  "email":"audit.new.student@example.com",
                                  "password":"%s",
                                  "role":"STUDENT"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID studentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        createdUser.getResponse().getContentAsString(), "$.data.id")
                .toString());

        assertLatest(AuditAction.USER_CREATED, AuditEntityTypes.USER, studentId, admin.getId());
        assertNoSecretsInAudits();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", studentId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CLASS_REP\"}"))
                .andExpect(status().isOk());
        assertLatest(AuditAction.USER_ROLE_CHANGED, AuditEntityTypes.USER, studentId, admin.getId());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", studentId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        assertLatest(AuditAction.USER_STATUS_CHANGED, AuditEntityTypes.USER, studentId, admin.getId());

        MvcResult unitCreated = mockMvc.perform(post("/api/v1/course-units")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Research Methods","code":"BIT 3102"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID unitId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        unitCreated.getResponse().getContentAsString(), "$.data.id")
                .toString());
        AuditLog unitCreate = assertLatest(
                AuditAction.COURSE_UNIT_CREATED, AuditEntityTypes.COURSE_UNIT, unitId, null);
        assertThat(unitCreate.getActorEmail()).isEqualTo("audit.rep@example.com");

        mockMvc.perform(patch("/api/v1/course-units/{id}", unitId)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerName\":\"Dr. Ada\"}"))
                .andExpect(status().isOk());
        assertLatest(AuditAction.COURSE_UNIT_UPDATED, AuditEntityTypes.COURSE_UNIT, unitId, null);

        mockMvc.perform(patch("/api/v1/course-units/{id}/status", unitId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        assertLatest(AuditAction.COURSE_UNIT_STATUS_CHANGED, AuditEntityTypes.COURSE_UNIT, unitId, admin.getId());

        Instant due = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID courseworkId = createDraft("Audited CW", due);
        assertLatest(AuditAction.COURSEWORK_CREATED, AuditEntityTypes.COURSEWORK, courseworkId, admin.getId());

        mockMvc.perform(patch("/api/v1/coursework/{id}", courseworkId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audited CW Updated\"}"))
                .andExpect(status().isOk());
        assertLatest(AuditAction.COURSEWORK_UPDATED, AuditEntityTypes.COURSEWORK, courseworkId, admin.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "brief.pdf",
                "application/pdf",
                "pdf-bytes".getBytes(StandardCharsets.UTF_8));
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", courseworkId)
                        .file(file)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID attachmentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        uploaded.getResponse().getContentAsString(), "$.data.id")
                .toString());
        assertLatest(AuditAction.ATTACHMENT_UPLOADED, AuditEntityTypes.ATTACHMENT, attachmentId, admin.getId());

        mockMvc.perform(delete("/api/v1/coursework/{cw}/attachments/{id}", courseworkId, attachmentId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertLatest(AuditAction.ATTACHMENT_DELETED, AuditEntityTypes.ATTACHMENT, attachmentId, admin.getId());

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", courseworkId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertLatest(AuditAction.COURSEWORK_PUBLISHED, AuditEntityTypes.COURSEWORK, courseworkId, admin.getId());

        mockMvc.perform(post("/api/v1/coursework/{id}/archive", courseworkId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertLatest(AuditAction.COURSEWORK_ARCHIVED, AuditEntityTypes.COURSEWORK, courseworkId, admin.getId());

        UUID cancelId = createDraft("Cancel Me", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", cancelId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertLatest(AuditAction.COURSEWORK_CANCELLED, AuditEntityTypes.COURSEWORK, cancelId, admin.getId());

        MvcResult announcement = mockMvc.perform(post("/api/v1/announcements")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audited Ann\",\"content\":\"Safe summary only.\",\"classId\":\""
                                + defaultClass.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID announcementId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        announcement.getResponse().getContentAsString(), "$.data.id")
                .toString());
        assertLatest(AuditAction.ANNOUNCEMENT_CREATED, AuditEntityTypes.ANNOUNCEMENT, announcementId, admin.getId());

        mockMvc.perform(patch("/api/v1/announcements/{id}", announcementId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audited Ann 2\"}"))
                .andExpect(status().isOk());
        assertLatest(AuditAction.ANNOUNCEMENT_UPDATED, AuditEntityTypes.ANNOUNCEMENT, announcementId, admin.getId());

        mockMvc.perform(post("/api/v1/announcements/{id}/publish", announcementId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertLatest(AuditAction.ANNOUNCEMENT_PUBLISHED, AuditEntityTypes.ANNOUNCEMENT, announcementId, admin.getId());

        mockMvc.perform(post("/api/v1/announcements/{id}/archive", announcementId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertLatest(AuditAction.ANNOUNCEMENT_ARCHIVED, AuditEntityTypes.ANNOUNCEMENT, announcementId, admin.getId());

        mockMvc.perform(post("/api/v1/notes")
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"Private note",
                                  "rawContent":"%s"
                                }
                                """.formatted(courseUnit.getId(), SECRET_NOTE)))
                .andExpect(status().isCreated());

        assertNoSecretsInAudits();
        List<Map<String, Object>> summaries = jdbcTemplate.queryForList(
                "select summary from audit_logs where summary like ?", "%" + SECRET_NOTE + "%");
        assertThat(summaries).isEmpty();
    }

    @Test
    void auditLogEndpointIsAdminOnlyWithFiltersPaginationAndNewestFirst() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"One",
                                  "lastName":"Student",
                                  "email":"audit.filter.one@example.com",
                                  "password":"%s",
                                  "role":"STUDENT"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated());

        Instant due = Instant.now().plus(8, ChronoUnit.DAYS);
        UUID courseworkId = createDraft("Filter CW", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", courseworkId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit-logs").session(studentSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit-logs").session(classRepSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .session(adminSession)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        String page1 = mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .session(adminSession)
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> createdAts = com.jayway.jsonpath.JsonPath.read(page1, "$.data[*].createdAt");
        assertThat(createdAts).isSortedAccordingTo((a, b) -> Instant.parse(b).compareTo(Instant.parse(a)));
        assertThat(page1).doesNotContain(PASSWORD);
        assertThat(page1).doesNotContain("passwordHash");
        assertThat(page1).doesNotContain(SECRET_NOTE);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .session(adminSession)
                        .param("action", "COURSEWORK_PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("COURSEWORK_PUBLISHED"))
                .andExpect(jsonPath("$.data[0].actorUserId").value(admin.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .session(adminSession)
                        .param("actorUserId", admin.getId().toString())
                        .param("entityType", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].entityType").value("USER"))
                .andExpect(jsonPath("$.data[0].actorUserId").value(admin.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .session(adminSession)
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    private AuditLog assertLatest(
            AuditAction action, String entityType, UUID entityId, UUID expectedActorId) {
        AuditLog latest = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == action
                        && entityType.equals(log.getEntityType())
                        && entityId.equals(log.getEntityId()))
                .findFirst()
                .orElseThrow();
        if (expectedActorId != null) {
            assertThat(latest.getActorUserId()).isEqualTo(expectedActorId);
        }
        assertThat(latest.getSummary()).isNotBlank();
        assertThat(latest.getSummary().toLowerCase()).doesNotContain("password");
        return latest;
    }

    private void assertNoSecretsInAudits() {
        for (AuditLog log : auditLogRepository.findAll()) {
            assertThat(log.getSummary()).doesNotContain(PASSWORD);
            assertThat(log.getSummary()).doesNotContain(SECRET_NOTE);
            assertThat(log.getSummary().toLowerCase()).doesNotContain("passwordhash");
            assertThat(log.getSummary().toLowerCase()).doesNotContain("$argon2");
        }
    }

    private UUID createDraft(String title, Instant due) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"%s",
                                  "description":"Audit coursework",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(courseUnit.getId(), title, due)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                        created.getResponse().getContentAsString(), "$.data.id")
                .toString());
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
