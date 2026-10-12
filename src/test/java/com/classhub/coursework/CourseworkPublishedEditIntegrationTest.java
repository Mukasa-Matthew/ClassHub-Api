package com.classhub.coursework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import com.classhub.notification.NotificationRepository;
import com.classhub.notification.NotificationType;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class CourseworkPublishedEditIntegrationTest {

    private static final String PASSWORD = "PubEditPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private CourseworkRepository courseworkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
                "Super", "Admin", "pubedit.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "pubedit.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "pubedit.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("pubedit.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("pubedit.student@example.com"));

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-880001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));
        otherUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-880002", null, "IT Research", "it research", null, null, true));

        adminSession = login("pubedit.admin@example.com");
        classRepSession = login("pubedit.rep@example.com");
        studentSession = login("pubedit.student@example.com");
    }

    @Test
    void classRepCanEditAllPublishedFieldsExceptCourseUnit() throws Exception {
        Instant due = Instant.now().plus(14, ChronoUnit.DAYS);
        Instant issued = Instant.now().plus(1, ChronoUnit.DAYS);
        UUID id = createAndPublish("Phase 7 Dev Coursework", due);

        Instant newDue = due.plus(3, ChronoUnit.DAYS);
        Instant newIssued = issued.plus(2, ChronoUnit.DAYS);

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Phase 7 Updated",
                                  "description":"Updated description",
                                  "instructions":"Download the revised brief",
                                  "type":"GROUP_PROJECT",
                                  "issuedAt":"%s",
                                  "dueAt":"%s",
                                  "weight":15,
                                  "sourceType":"MOODLE",
                                  "sourceUrl":"https://example.edu/moodle/assign/99",
                                  "sourceLabel":"Moodle link"
                                }
                                """.formatted(newIssued, newDue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.title").value("Phase 7 Updated"))
                .andExpect(jsonPath("$.data.type").value("GROUP_PROJECT"))
                .andExpect(jsonPath("$.data.weight").value(15))
                .andExpect(jsonPath("$.data.sourceType").value("MOODLE"));

        Coursework updated = courseworkRepository.findById(id).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CourseworkStatus.PUBLISHED);
        assertThat(updated.getCourseUnit().getId()).isEqualTo(courseUnit.getId());

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseUnitId\":\"" + otherUnit.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_COURSEWORK_STATE));
    }

    @Test
    void publishedEditDoesNotCreateSecondPublishNotification() throws Exception {
        Instant due = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = createAndPublish("No Republish Notify", due);

        long publishNotificationsBefore = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.COURSEWORK_PUBLISHED)
                .filter(n -> id.equals(n.getReferenceId()))
                .count();
        assertThat(publishNotificationsBefore).isEqualTo(2);

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed Work\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        long publishNotificationsAfter = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.COURSEWORK_PUBLISHED)
                .filter(n -> id.equals(n.getReferenceId()))
                .count();
        assertThat(publishNotificationsAfter).isEqualTo(2);
    }

    @Test
    void deadlineChangeNotifiesInstructionsOnlyWhenRequested() throws Exception {
        Instant due = Instant.now().plus(12, ChronoUnit.DAYS);
        UUID id = createAndPublish("Notify Rules Work", due);

        Instant newDue = due.plus(2, ChronoUnit.DAYS);
        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + newDue + "\"}"))
                .andExpect(status().isOk());

        assertThat(countNotifications(id, NotificationType.COURSEWORK_DEADLINE_CHANGED)).isEqualTo(2);

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + newDue + "\"}"))
                .andExpect(status().isOk());
        assertThat(countNotifications(id, NotificationType.COURSEWORK_DEADLINE_CHANGED)).isEqualTo(2);

        UUID draftId = createDraft("Draft Only", due.plus(5, ChronoUnit.DAYS));
        mockMvc.perform(patch("/api/v1/coursework/{id}", draftId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + due.plus(20, ChronoUnit.DAYS) + "\"}"))
                .andExpect(status().isOk());
        assertThat(countNotifications(draftId, NotificationType.COURSEWORK_DEADLINE_CHANGED)).isZero();

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"Same steps\",\"notifyStudentsOfUpdate\":false}"))
                .andExpect(status().isOk());
        assertThat(countNotifications(id, NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED)).isZero();

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"  Same   steps  \",\"notifyStudentsOfUpdate\":true}"))
                .andExpect(status().isOk());
        assertThat(countNotifications(id, NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED)).isZero();

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"Final steps\",\"notifyStudentsOfUpdate\":true}"))
                .andExpect(status().isOk());
        assertThat(countNotifications(id, NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED)).isEqualTo(2);
    }

    @Test
    void cancelledAndArchivedCannotBeEdited() throws Exception {
        Instant due = Instant.now().plus(8, ChronoUnit.DAYS);
        UUID publishedId = createAndPublish("To Cancel", due);

        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", publishedId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/coursework/{id}", publishedId)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isConflict());

        UUID archiveId = createAndPublish("To Archive", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/archive", archiveId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/coursework/{id}", archiveId)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void studentCannotUpdateCoursework() throws Exception {
        UUID id = createAndPublish("Student Blocked", Instant.now().plus(9, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishedAttachmentUploadAndDeleteByClassRep() throws Exception {
        UUID id = createAndPublish("Attachment Published", Instant.now().plus(11, ChronoUnit.DAYS));

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "brief.pdf", "application/pdf", "brief body".getBytes());

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", id)
                        .file(pdf)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isCreated());

        MvcResult list = mockMvc.perform(get("/api/v1/coursework/{id}/attachments", id).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        UUID attachmentId = UUID.fromString(json(list, "$.data[0].id"));

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", id)
                        .file(pdf)
                        .session(studentSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/coursework/{id}/attachments/{aid}", id, attachmentId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private long countNotifications(UUID courseworkId, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> type == n.getType())
                .filter(n -> courseworkId.equals(n.getReferenceId()))
                .count();
    }

    private UUID createDraft(String title, Instant dueAt) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"%s",
                                  "description":"Published edit test",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "weight":10,
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(courseUnit.getId(), title, dueAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(created, "$.data.id"));
    }

    private UUID createAndPublish(String title, Instant dueAt) throws Exception {
        UUID id = createDraft(title, dueAt);
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id)
                        .session(classRepSession)
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
