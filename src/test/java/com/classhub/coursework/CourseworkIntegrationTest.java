package com.classhub.coursework;

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
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class CourseworkIntegrationTest {

    private static final String PASSWORD = "CourseworkPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private CourseworkRepository courseworkRepository;

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
    private CourseUnit courseUnit;
    private CourseUnit otherUnit;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        userService.create(new CreateUserCommand(
                "Super", "Admin", "cw.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "cw.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "cw.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("cw.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("cw.student@example.com"));

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-992001", "BIT 3204", "Cyber Security", "cyber security", "Dr Example", null, true));
        otherUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-992002", null, "IT Research", "it research", null, null, true));

        adminSession = login("cw.admin@example.com");
        classRepSession = login("cw.rep@example.com");
        studentSession = login("cw.student@example.com");
    }

    @Test
    void createRulesAndValidation() throws Exception {
        Instant due = Instant.now().plus(14, ChronoUnit.DAYS);
        Instant issued = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Cyber Security Assignment 1", due, issued,
                                "10", "MOODLE", "https://example.edu/moodle/mod/assign/view.php?id=1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedAt").isEmpty())
                .andExpect(jsonPath("$.data.courseUnitName").value("Cyber Security"))
                .andExpect(jsonPath("$.data.createdByName").value("Class Rep"))
                .andExpect(jsonPath("$.data.sourceType").value("MOODLE"));

        mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Admin Draft", due, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/coursework")
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Student Attempt", due, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), "Missing Unit", due, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_NOT_FOUND));

        Instant pastDue = Instant.now().minus(1, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Past Due", pastDue, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_DEADLINE));

        Instant issuedAfterDue = due.plus(1, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Bad Order", due, issuedAfterDue,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_DEADLINE));

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Bad Weight", due, null,
                                "0", "DIRECT_ENTRY", null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Moodle No Url", due, null,
                                null, "MOODLE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_SOURCE));

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Malformed Url", due, null,
                                null, "EXTERNAL_LINK", "not-a-url")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_SOURCE));

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Javascript Url", due, null,
                                null, "EXTERNAL_LINK", "javascript:alert(1)")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_SOURCE));

        mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "File Url", due, null,
                                null, "EXTERNAL_LINK", "file:///etc/passwd")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_SOURCE));
    }

    @Test
    void updatePublishCancelArchiveAndVisibility() throws Exception {
        Instant due = Instant.now().plus(10, ChronoUnit.DAYS);

        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Draft Title", due, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(jsonPathString(created, "$.data.id"));

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Updated Draft",
                                  "courseUnitId":"%s",
                                  "sourceType":"MOODLE",
                                  "sourceUrl":"https://example.edu/moodle/assign/2",
                                  "sourceLabel":"Open in Moodle"
                                }
                                """.formatted(otherUnit.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Draft"))
                .andExpect(jsonPath("$.data.courseUnitName").value("IT Research"))
                .andExpect(jsonPath("$.data.sourceType").value("MOODLE"));

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/coursework/{id}", id).session(studentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSEWORK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/coursework/{id}", id).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/coursework/{id}", id).session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/coursework/{id}/archive", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_STATE));

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        Coursework published = courseworkRepository.findById(id).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getStatus()).isEqualTo(CourseworkStatus.PUBLISHED);

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Published Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Published Title"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/coursework/{id}", id).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        MvcResult draftForCancel = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Cancel Draft", due, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID draftCancelId = UUID.fromString(jsonPathString(draftForCancel, "$.data.id"));

        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", draftCancelId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/coursework/{id}", draftCancelId).session(studentSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", draftCancelId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_STATE));

        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", id)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/coursework/{id}", id).session(studentSession))
                .andExpect(status().isNotFound());

        MvcResult toArchive = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Archive Me", due, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID archiveId = UUID.fromString(jsonPathString(toArchive, "$.data.id"));

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", archiveId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/coursework/{id}/archive", archiveId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/coursework/{id}", archiveId).session(studentSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", archiveId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_STATE));

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(studentSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingFiltersAndPagination() throws Exception {
        Instant upcomingDue = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant laterDue = Instant.now().plus(21, ChronoUnit.DAYS);

        UUID upcomingId = createAndPublish(classRepSession, courseUnit.getId(), "Upcoming Work", upcomingDue);
        UUID otherUnitPublishedId = createAndPublish(adminSession, otherUnit.getId(), "Other Unit Work", laterDue);

        MvcResult overdueDraft = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Will Be Overdue", upcomingDue, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID overdueId = UUID.fromString(jsonPathString(overdueDraft, "$.data.id"));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", overdueId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "update coursework set due_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)),
                overdueId);

        mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(courseUnit.getId(), "Hidden Draft", upcomingDue, null,
                                null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/coursework")
                        .session(studentSession)
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(classRepSession)
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(adminSession)
                        .param("courseUnitId", courseUnit.getId().toString())
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(studentSession)
                        .param("courseUnitId", otherUnit.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(otherUnitPublishedId.toString()));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(studentSession)
                        .param("upcoming", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(adminSession)
                        .param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(overdueId.toString()));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(adminSession)
                        .param("upcoming", "true")
                        .param("overdue", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_DATA));

        mockMvc.perform(get("/api/v1/coursework")
                        .session(adminSession)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(4))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        assertThat(upcomingId).isNotNull();
    }

    private UUID createAndPublish(MockHttpSession session, UUID unitId, String title, Instant due)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(unitId, title, due, null, null, "DIRECT_ENTRY", null)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(jsonPathString(created, "$.data.id"));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private static String createBody(
            UUID courseUnitId,
            String title,
            Instant dueAt,
            Instant issuedAt,
            String weight,
            String sourceType,
            String sourceUrl) {
        String issued = issuedAt == null ? "null" : "\"" + issuedAt + "\"";
        String weightJson = weight == null ? "null" : weight;
        String urlJson = sourceUrl == null ? "null" : "\"" + sourceUrl + "\"";
        return """
                {
                  "courseUnitId":"%s",
                  "title":"%s",
                  "description":"Research common social engineering attacks.",
                  "instructions":"Submit your report through Moodle.",
                  "type":"ASSIGNMENT",
                  "issuedAt":%s,
                  "dueAt":"%s",
                  "weight":%s,
                  "sourceType":"%s",
                  "sourceUrl":%s,
                  "sourceLabel":"Open source"
                }
                """.formatted(courseUnitId, title, issued, dueAt, weightJson, sourceType, urlJson);
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

    private static String jsonPathString(MvcResult result, String path) throws Exception {
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, path).toString();
    }
}
