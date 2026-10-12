package com.classhub.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class DashboardIntegrationTest {

    private static final String PASSWORD = "DashPassw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentASession;
    private MockHttpSession studentBSession;
    private User admin;
    private User studentA;
    private CourseUnit courseUnit;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        admin = userService.create(new CreateUserCommand(
                "Super", "Admin", "dash.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "dash.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        studentA = userService.create(new CreateUserCommand(
                "Stu", "A", "dash.student.a@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "B", "dash.student.b@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("dash.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, studentA);
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("dash.student.b@example.com"));

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-998001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));
        courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-998002", "BIT 3101", "Inactive Unit", "inactive unit", null, null, false));

        adminSession = login("dash.admin@example.com");
        classRepSession = login("dash.rep@example.com");
        studentASession = login("dash.student.a@example.com");
        studentBSession = login("dash.student.b@example.com");
    }

    @Test
    void studentDashboardAccessibleOnlyToStudentsAndCountsCorrectly() throws Exception {
        Instant soon = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant later = Instant.now().plus(20, ChronoUnit.DAYS);
        Instant far = Instant.now().plus(40, ChronoUnit.DAYS);

        UUID pendingId = createPublish("Pending Work", soon);
        UUID inProgressId = createPublish("In Progress Work", later);
        UUID completedId = createPublish("Completed Work", far);
        UUID overdueId = createPublish("Overdue Work", Instant.now().plus(2, ChronoUnit.DAYS));
        UUID overdueCompletedId = createPublish("Overdue Completed", Instant.now().plus(2, ChronoUnit.DAYS));

        backdateDue(overdueId, Instant.now().minus(2, ChronoUnit.DAYS));
        backdateDue(overdueCompletedId, Instant.now().minus(1, ChronoUnit.DAYS));

        setProgress(studentASession, inProgressId, "IN_PROGRESS");
        setProgress(studentASession, completedId, "COMPLETED");
        setProgress(studentASession, overdueCompletedId, "COMPLETED");
        setProgress(studentBSession, pendingId, "COMPLETED");

        UUID draftAnnouncement = createAnnouncementDraft("Hidden Draft");
        UUID publishedAnnouncement = createAnnouncementDraft("Published Note");
        mockMvc.perform(post("/api/v1/announcements/{id}/publish", publishedAnnouncement)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/student").session(classRepSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dashboard/student").session(adminSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/student").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActiveCourseUnits").value(1))
                .andExpect(jsonPath("$.data.pendingCourseworkCount").value(2))
                .andExpect(jsonPath("$.data.inProgressCourseworkCount").value(1))
                .andExpect(jsonPath("$.data.completedCourseworkCount").value(2))
                .andExpect(jsonPath("$.data.overdueCourseworkCount").value(1))
                .andExpect(jsonPath("$.data.dueSoonCourseworkCount").value(1))
                .andExpect(jsonPath("$.data.unreadNotificationCount").value(6))
                .andExpect(jsonPath("$.data.recentAnnouncements.length()").value(1))
                .andExpect(jsonPath("$.data.recentAnnouncements[0].id").value(publishedAnnouncement.toString()))
                .andExpect(jsonPath("$.data.recentAnnouncements[0].title").value("Published Note"))
                .andExpect(jsonPath("$.data.upcomingCoursework[0].courseworkId").value(overdueId.toString()))
                .andExpect(jsonPath("$.data.upcomingCoursework[1].courseworkId").value(overdueCompletedId.toString()))
                .andExpect(jsonPath("$.data.upcomingCoursework[2].courseworkId").value(pendingId.toString()))
                .andExpect(jsonPath("$.data.upcomingCoursework[2].myProgress.status").value("NOT_STARTED"));

        assertThat(draftAnnouncement).isNotNull();

        String body = mockMvc.perform(get("/api/v1/dashboard/student").session(studentASession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("dash.student.b@example.com");
        assertThat(body).doesNotContain("completionRate");
        assertThat(body).doesNotContain("rank");
    }

    @Test
    void classRepAndAdminDashboardsAreRoleScopedAndAggregateOnly() throws Exception {
        Instant soon = Instant.now().plus(4, ChronoUnit.DAYS);
        UUID draftCw = createDraft("Draft CW", soon);
        UUID publishedCw = createPublish("Published CW", soon);
        UUID overdueCw = createPublish("Overdue CW", Instant.now().plus(1, ChronoUnit.DAYS));
        backdateDue(overdueCw, Instant.now().minus(3, ChronoUnit.DAYS));

        UUID draftAnn = createAnnouncementDraft("Draft Ann");
        UUID publishedAnn = createAnnouncementDraft("Published Ann");
        mockMvc.perform(post("/api/v1/announcements/{id}/publish", publishedAnn)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        setProgress(studentASession, publishedCw, "COMPLETED");

        mockMvc.perform(get("/api/v1/dashboard/class-rep").session(studentASession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/class-rep").session(adminSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/class-rep").session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCourseUnits").value(1))
                .andExpect(jsonPath("$.data.draftCoursework").value(1))
                .andExpect(jsonPath("$.data.publishedCoursework").value(2))
                .andExpect(jsonPath("$.data.overduePublishedCoursework").value(1))
                .andExpect(jsonPath("$.data.draftAnnouncements").value(1))
                .andExpect(jsonPath("$.data.publishedAnnouncements").value(1))
                .andExpect(jsonPath("$.data.activeStudentCount").value(2))
                .andExpect(jsonPath("$.data.upcomingDeadlines.length()").value(1))
                .andExpect(jsonPath("$.data.upcomingDeadlines[0].courseworkId").value(publishedCw.toString()))
                .andExpect(jsonPath("$.data.recentAnnouncements[0].id").value(publishedAnn.toString()));

        String classRepBody = mockMvc.perform(get("/api/v1/dashboard/class-rep").session(classRepSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(classRepBody).doesNotContain("myProgress");
        assertThat(classRepBody).doesNotContain("lectureNote");
        assertThat(classRepBody).doesNotContain(studentA.getId().toString());
        assertThat(draftCw).isNotNull();
        assertThat(draftAnn).isNotNull();

        mockMvc.perform(get("/api/v1/dashboard/admin").session(studentASession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/admin").session(classRepSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/admin").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(4))
                .andExpect(jsonPath("$.data.activeStudents").value(2))
                .andExpect(jsonPath("$.data.activeClassReps").value(1))
                .andExpect(jsonPath("$.data.suspendedUsers").value(0))
                .andExpect(jsonPath("$.data.disabledUsers").value(0))
                .andExpect(jsonPath("$.data.activeCourseUnits").value(1))
                .andExpect(jsonPath("$.data.totalPublishedCoursework").value(2))
                .andExpect(jsonPath("$.data.totalPublishedAnnouncements").value(1))
                .andExpect(jsonPath("$.data.totalAttachments").value(0))
                .andExpect(jsonPath("$.data.recentAdministrativeActions").isArray());

        String adminBody = mockMvc.perform(get("/api/v1/dashboard/admin").session(adminSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(adminBody).doesNotContain("myProgress");
        assertThat(adminBody).doesNotContain("PRIVATE_NOTE_SECRET");
        assertThat(admin.getId()).isNotNull();
    }

    private void setProgress(MockHttpSession session, UUID courseworkId, String status) throws Exception {
        mockMvc.perform(put("/api/v1/coursework/{id}/progress", courseworkId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk());
    }

    private void backdateDue(UUID courseworkId, Instant dueAt) {
        jdbcTemplate.update(
                "update coursework set due_at = ? where id = ?",
                Timestamp.from(dueAt),
                courseworkId);
        entityManager.flush();
        entityManager.clear();
    }

    private UUID createPublish(String title, Instant due) throws Exception {
        UUID id = createDraft(title, due);
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        return id;
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
                                  "description":"Dashboard coursework",
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

    private UUID createAnnouncementDraft(String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/announcements")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"Announcement body for dashboard tests.","classId":"%s"}
                                """.formatted(title, defaultClass.getId())))
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
