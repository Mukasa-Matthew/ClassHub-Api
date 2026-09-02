package com.classhub.coursework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
class CourseworkProgressIntegrationTest {

    private static final String PASSWORD = "ProgressPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private CourseworkRepository courseworkRepository;

    @Autowired
    private CourseworkProgressRepository progressRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentASession;
    private MockHttpSession studentBSession;
    private User studentA;
    private User studentB;
    private UUID publishedId;
    private UUID draftId;
    private UUID cancelledId;
    private UUID archivedId;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        userService.create(new CreateUserCommand(
                "Super", "Admin", "prog.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "prog.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        studentA = userService.create(new CreateUserCommand(
                "Stu", "A", "prog.student.a@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        studentB = userService.create(new CreateUserCommand(
                "Stu", "B", "prog.student.b@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("prog.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, studentA);
        membershipTestSupport.activateStudent(defaultClass, studentB);

        CourseUnit unit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-999001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));

        adminSession = login("prog.admin@example.com");
        classRepSession = login("prog.rep@example.com");
        studentASession = login("prog.student.a@example.com");
        studentBSession = login("prog.student.b@example.com");

        Instant due = Instant.now().plus(14, ChronoUnit.DAYS);
        publishedId = createPublish(unit.getId(), "Published Work", due);
        draftId = createDraft(unit.getId(), "Draft Work", due);
        cancelledId = createPublish(unit.getId(), "Cancelled Work", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", cancelledId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        archivedId = createPublish(unit.getId(), "Archived Work", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/archive", archivedId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void defaultProgressDoesNotCreateRowAndUpsertLifecycleWorks() throws Exception {
        mockMvc.perform(get("/api/v1/coursework/{id}/progress", publishedId).session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseworkId").value(publishedId.toString()))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());

        assertThat(progressRepository.existsByCourseworkIdAndStudentId(publishedId, studentA.getId()))
                .isFalse();

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());

        assertThat(progressRepository.countByCourseworkIdAndStudentId(publishedId, studentA.getId())).isEqualTo(1);

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        assertThat(progressRepository.countByCourseworkIdAndStudentId(publishedId, studentA.getId())).isEqualTo(1);

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        Instant firstCompletedAt = progressRepository
                .findByCourseworkIdAndStudentId(publishedId, studentA.getId())
                .orElseThrow()
                .getCompletedAt();
        assertThat(firstCompletedAt).isNotNull();

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        Instant preserved = progressRepository
                .findByCourseworkIdAndStudentId(publishedId, studentA.getId())
                .orElseThrow()
                .getCompletedAt();
        assertThat(preserved).isEqualTo(firstCompletedAt);

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_STARTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());
    }

    @Test
    void privacyAuthorizationAndPublishedOnly() throws Exception {
        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentBSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/coursework/{id}/progress", publishedId).session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/coursework/{id}/progress", publishedId).session(studentBSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        assertThat(progressRepository.countByCourseworkIdAndStudentId(publishedId, studentA.getId())).isEqualTo(1);
        assertThat(progressRepository.countByCourseworkIdAndStudentId(publishedId, studentB.getId())).isEqualTo(1);

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", draftId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSEWORK_NOT_FOUND));

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", cancelledId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", archivedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/coursework/{id}/progress", publishedId).session(classRepSession))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coursework/{id}/progress", publishedId).session(adminSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/coursework/{id}/progress", publishedId))
                .andExpect(status().isUnauthorized());

        Timestamp now = Timestamp.from(Instant.now());
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into coursework_progress
                        (id, coursework_id, student_id, progress_status, completed_at, created_at, updated_at)
                        values (?, ?, ?, 'IN_PROGRESS', null, ?, ?)
                        """,
                        UUID.randomUUID(),
                        publishedId,
                        studentA.getId(),
                        now,
                        now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void courseworkListAndDetailIncludeMyProgressForStudentsOnly() throws Exception {
        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coursework/{id}", publishedId).session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myProgress.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.myProgress.completedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/coursework/{id}", publishedId).session(studentBSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myProgress.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.myProgress.completedAt").isEmpty());

        mockMvc.perform(get("/api/v1/coursework").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='%s')].myProgress.status".formatted(publishedId))
                        .value(org.hamcrest.Matchers.hasItem("COMPLETED")));

        mockMvc.perform(put("/api/v1/coursework/{id}/progress", publishedId)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coursework/{id}", publishedId).session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myProgress.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/coursework/{id}", publishedId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myProgress").doesNotExist());

        mockMvc.perform(get("/api/v1/coursework").session(adminSession).param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].myProgress").doesNotExist());
    }

    private UUID createPublish(UUID unitId, String title, Instant due) throws Exception {
        UUID id = createDraft(unitId, title, due);
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private UUID createDraft(UUID unitId, String title, Instant due) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"%s",
                                  "description":"Progress test coursework",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(unitId, title, due)))
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
