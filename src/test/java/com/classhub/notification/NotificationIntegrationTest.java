package com.classhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class NotificationIntegrationTest {

    private static final String PASSWORD = "NotifyPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
    private User classRep;
    private User admin;
    private User suspended;
    private User disabled;
    private CourseUnit courseUnit;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        admin = userService.create(new CreateUserCommand(
                "Super", "Admin", "ntf.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        classRep = userService.create(new CreateUserCommand(
                "Class", "Rep", "ntf.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        studentA = userService.create(new CreateUserCommand(
                "Stu", "A", "ntf.student.a@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        studentB = userService.create(new CreateUserCommand(
                "Stu", "B", "ntf.student.b@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        suspended = userService.create(new CreateUserCommand(
                "Sus", "Pended", "ntf.suspended@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.SUSPENDED, false));
        disabled = userService.create(new CreateUserCommand(
                "Dis", "Abled", "ntf.disabled@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.DISABLED, false));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, classRep);
        membershipTestSupport.activateStudent(defaultClass, studentA);
        membershipTestSupport.activateStudent(defaultClass, studentB);

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-994001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));

        adminSession = login("ntf.admin@example.com");
        classRepSession = login("ntf.rep@example.com");
        studentASession = login("ntf.student.a@example.com");
        studentBSession = login("ntf.student.b@example.com");
    }

    @Test
    void publishingCreatesNotificationsForActiveStudentsOnly() throws Exception {
        UUID courseworkId = createAndPublishCoursework("Cyber Security Assignment 1");

        List<Notification> courseworkNotes = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.COURSEWORK_PUBLISHED)
                .filter(n -> courseworkId.equals(n.getReferenceId()))
                .toList();

        assertThat(courseworkNotes).hasSize(3);
        assertThat(courseworkNotes)
                .extracting(n -> n.getRecipient().getId())
                .containsExactlyInAnyOrder(studentA.getId(), studentB.getId(), classRep.getId());
        assertThat(courseworkNotes)
                .extracting(n -> n.getRecipient().getId())
                .doesNotContain(admin.getId(), suspended.getId(), disabled.getId());

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", courseworkId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isConflict());
        assertThat(notificationRepository.findAll().stream()
                        .filter(n -> courseworkId.equals(n.getReferenceId()))
                        .count())
                .isEqualTo(3);

        MvcResult ann = mockMvc.perform(post("/api/v1/announcements")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lecture Time","content":"Tomorrow's IT Research lecture begins at 10:00 AM."}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID announcementId = UUID.fromString(json(ann, "$.data.id"));

        mockMvc.perform(post("/api/v1/announcements/{id}/publish", announcementId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        List<Notification> announcementNotes = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.ANNOUNCEMENT_PUBLISHED)
                .filter(n -> announcementId.equals(n.getReferenceId()))
                .toList();
        assertThat(announcementNotes).hasSize(3);
        assertThat(announcementNotes)
                .extracting(n -> n.getRecipient().getId())
                .containsExactlyInAnyOrder(studentA.getId(), studentB.getId(), classRep.getId());
    }

    @Test
    void inboxPrivacyReadAndFilters() throws Exception {
        createAndPublishCoursework("First Work");
        createAndPublishCoursework("Second Work");

        mockMvc.perform(get("/api/v1/notifications/unread-count").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        mockMvc.perform(get("/api/v1/notifications").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));

        String firstCreated = json(
                mockMvc.perform(get("/api/v1/notifications").session(studentASession))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.data[0].createdAt");
        String secondCreated = json(
                mockMvc.perform(get("/api/v1/notifications").session(studentASession))
                        .andExpect(status().isOk())
                        .andReturn(),
                "$.data[1].createdAt");
        assertThat(Instant.parse(firstCreated)).isAfterOrEqualTo(Instant.parse(secondCreated));

        UUID studentANoteId = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(studentA.getId()))
                .findFirst()
                .orElseThrow()
                .getId();
        UUID studentBNoteId = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(studentB.getId()))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/api/v1/notifications").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='%s')]".formatted(studentBNoteId)).isEmpty());

        mockMvc.perform(post("/api/v1/notifications/{id}/read", studentBNoteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/notifications/{id}/read", studentANoteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        Instant firstReadAt = notificationRepository.findById(studentANoteId).orElseThrow().getReadAt();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", studentANoteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk());
        assertThat(notificationRepository.findById(studentANoteId).orElseThrow().getReadAt())
                .isEqualTo(firstReadAt);

        mockMvc.perform(get("/api/v1/notifications/unread-count").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(get("/api/v1/notifications").session(studentASession).param("read", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/notifications").session(studentASession).param("read", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));

        mockMvc.perform(get("/api/v1/notifications/unread-count").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(get("/api/v1/notifications")
                        .session(studentASession)
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(1))
                .andExpect(jsonPath("$.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        mockMvc.perform(get("/api/v1/notifications/unread-count").session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));
    }

    private UUID createAndPublishCoursework(String title) throws Exception {
        Instant due = Instant.now().plus(10, ChronoUnit.DAYS);
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"%s",
                                  "description":"Notification test coursework",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(courseUnit.getId(), title, due)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json(created, "$.data.id"));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        return id;
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
