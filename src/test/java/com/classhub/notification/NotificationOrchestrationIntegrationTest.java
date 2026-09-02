package com.classhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.coursework.Coursework;
import com.classhub.coursework.CourseworkProgress;
import com.classhub.coursework.CourseworkProgressRepository;
import com.classhub.coursework.CourseworkProgressStatus;
import com.classhub.coursework.CourseworkRepository;
import com.classhub.coursework.CourseworkSourceType;
import com.classhub.coursework.CourseworkType;
import com.classhub.notification.config.NotificationProperties;
import com.classhub.notification.delivery.DeliveryResult;
import com.classhub.notification.delivery.NotificationDeliveryAdapter;
import com.classhub.notification.delivery.NotificationDeliveryRequest;
import com.classhub.notification.delivery.NotificationDeliveryWorker;
import com.classhub.notification.delivery.NotificationMessageResolver;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, NotificationOrchestrationIntegrationTest.FixedClockConfig.class})
@Transactional
class NotificationOrchestrationIntegrationTest {

    private static final String PASSWORD = "OrchNotify1!";
    private static final ZoneId ZONE = ZoneId.of("Africa/Kampala");
    private static final Instant FIXED_NOW = LocalDate.of(2026, 9, 1).atTime(10, 0).atZone(ZONE).toInstant();

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
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationReminderLogRepository reminderLogRepository;

    @Autowired
    private CourseworkProgressRepository progressRepository;

    @Autowired
    private DeadlineReminderScheduler reminderScheduler;

    @Autowired
    private NotificationDeliveryWorker deliveryWorker;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private NotificationMessageResolver messageResolver;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentASession;
    private User studentA;
    private User studentB;
    private User suspended;
    private User admin;
    private CourseUnit courseUnit;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        admin = userService.create(new CreateUserCommand(
                "Super", "Admin", "orch.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "orch.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        studentA = userService.create(new CreateUserCommand(
                "Stu", "A", "orch.student.a@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        studentB = userService.create(new CreateUserCommand(
                "Stu", "B", "orch.student.b@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        suspended = userService.create(new CreateUserCommand(
                "Sus", "Pended", "orch.suspended@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.SUSPENDED, false));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("orch.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, studentA);
        membershipTestSupport.activateStudent(defaultClass, studentB);

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-991001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));

        adminSession = login("orch.admin@example.com");
        classRepSession = login("orch.rep@example.com");
        studentASession = login("orch.student.a@example.com");
    }

    @Test
    void publishCourseworkCreatesInAppAndSkippedExternalDeliveries() throws Exception {
        UUID courseworkId = createDraftCoursework("Orchestration Assignment", daysFromNow(10));

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", courseworkId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        List<Notification> notes = notificationsFor(courseworkId, NotificationType.COURSEWORK_PUBLISHED);
        assertThat(notes).hasSize(3);
        assertThat(notes).extracting(n -> n.getRecipient().getId())
                .containsExactlyInAnyOrder(studentA.getId(), studentB.getId(), userService.getByEmail("orch.rep@example.com").getId());
        assertThat(notes.get(0).getTitle()).contains("Orchestration Assignment");
        assertThat(notes.get(0).getMessage()).contains("Cyber Security");

        List<NotificationDelivery> deliveries = deliveryRepository.findAll();
        assertThat(deliveries).hasSize(12);
        assertThat(deliveries.stream().filter(d -> d.getChannel() == NotificationChannel.EMAIL))
                .allMatch(d -> d.getStatus() == DeliveryStatus.SKIPPED);
        assertThat(deliveries.stream().filter(d -> d.getChannel() == NotificationChannel.WHATSAPP))
                .allMatch(d -> d.getStatus() == DeliveryStatus.SKIPPED);
        assertThat(deliveries.stream().filter(d -> d.getChannel() == NotificationChannel.PUSH))
                .allMatch(d -> d.getStatus() == DeliveryStatus.SKIPPED);

        flushDeliveriesForWorker();
        deliveryWorker.processPendingBatch();
        List<NotificationDelivery> afterProcess = deliveryRepository.findAll();
        assertThat(afterProcess.stream().filter(d -> d.getChannel() == NotificationChannel.IN_APP))
                .allMatch(d -> d.getStatus() == DeliveryStatus.SENT);

        mockMvc.perform(post("/api/v1/coursework/{id}/publish", courseworkId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isConflict());
        assertThat(notificationsFor(courseworkId, NotificationType.COURSEWORK_PUBLISHED)).hasSize(3);
    }

    @Test
    void publishAnnouncementCreatesNotificationsForActiveStudentsOnly() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/announcements")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Exam Brief","content":"Bring your student ID to the exam hall."}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID announcementId = UUID.fromString(json(created, "$.data.id"));

        mockMvc.perform(post("/api/v1/announcements/{id}/publish", announcementId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        List<Notification> notes = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.ANNOUNCEMENT_PUBLISHED)
                .filter(n -> announcementId.equals(n.getReferenceId()))
                .toList();
        assertThat(notes).hasSize(3);
        assertThat(notes).extracting(n -> n.getRecipient().getId())
                .doesNotContain(suspended.getId());
    }

    @Test
    void deadlineRemindersAreIdempotentAndSkipCompletedStudents() {
        Coursework coursework = savePublishedCoursework("Reminder Work", daysFromNow(7));
        progressRepository.saveAndFlush(new CourseworkProgress(
                coursework, studentA, CourseworkProgressStatus.COMPLETED, FIXED_NOW));

        reminderScheduler.runReminders();
        assertThat(notificationsFor(coursework.getId(), NotificationType.COURSEWORK_DEADLINE_REMINDER))
                .hasSize(2);
        assertThat(reminderLogRepository.findAll()).hasSize(2);

        reminderScheduler.runReminders();
        assertThat(notificationsFor(coursework.getId(), NotificationType.COURSEWORK_DEADLINE_REMINDER))
                .hasSize(2);
    }

    @Test
    void reminderWindowsSevenThreeOneZeroAndOverdue() {
        savePublishedCoursework("Seven Day", daysFromNow(7));
        savePublishedCoursework("Three Day", daysFromNow(3));
        savePublishedCoursework("One Day", daysFromNow(1));
        savePublishedCoursework("Today", daysFromNow(0));
        savePublishedCoursework("Overdue", daysFromNow(-2));

        reminderScheduler.runReminders();

        assertThat(notificationRepository.findAll().stream()
                        .filter(n -> n.getType() == NotificationType.COURSEWORK_DEADLINE_REMINDER)
                        .count())
                .isEqualTo(15);
        assertThat(reminderLogRepository.findAll()).hasSize(15);
    }

    @Test
    void publishedDeadlineChangeCreatesNotification() throws Exception {
        UUID id = createDraftCoursework("Deadline Change", daysFromNow(5));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        Instant newDue = daysFromNow(8);
        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + newDue + "\"}"))
                .andExpect(status().isOk());

        assertThat(notificationsFor(id, NotificationType.COURSEWORK_DEADLINE_CHANGED)).hasSize(3);

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + newDue + "\"}"))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_DEADLINE_CHANGED)).hasSize(3);

        Instant anotherDue = daysFromNow(9);
        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + anotherDue + "\"}"))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_DEADLINE_CHANGED)).hasSize(6);
    }

    @Test
    void draftDeadlineChangeDoesNotNotify() throws Exception {
        UUID id = createDraftCoursework("Draft No Notify", daysFromNow(5));
        Instant newDue = daysFromNow(8);
        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"" + newDue + "\"}"))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_DEADLINE_CHANGED)).isEmpty();
    }

    @Test
    void publishedCancellationNotifiesStudents() throws Exception {
        UUID id = createDraftCoursework("Cancel Me", daysFromNow(4));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", id).session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_CANCELLED)).hasSize(3);

        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", id).session(adminSession).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_CANCELLED)).hasSize(3);
    }

    @Test
    void draftCancellationDoesNotNotifyStudents() throws Exception {
        UUID id = createDraftCoursework("Draft Cancel", daysFromNow(4));
        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", id).session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_CANCELLED)).isEmpty();
    }

    @Test
    void instructionsUpdateRequiresExplicitNotifyFlag() throws Exception {
        UUID id = createDraftCoursework("Instructions Work", daysFromNow(6));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"   \"}"))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED)).isEmpty();

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"Submit PDF only\",\"notifyStudentsOfUpdate\":false}"))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED)).isEmpty();

        mockMvc.perform(patch("/api/v1/coursework/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instructions\":\"Submit PDF and cover sheet\",\"notifyStudentsOfUpdate\":true}"))
                .andExpect(status().isOk());
        assertThat(notificationsFor(id, NotificationType.COURSEWORK_INSTRUCTIONS_UPDATED)).hasSize(3);
    }

    @Test
    void studentNotificationPreferences() throws Exception {
        mockMvc.perform(get("/api/v1/me/notification-preferences").session(classRepSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/notification-preferences").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailEnabled").value(true))
                .andExpect(jsonPath("$.data.whatsappEnabled").value(false));

        mockMvc.perform(put("/api/v1/me/notification-preferences")
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"whatsappEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailEnabled").value(false))
                .andExpect(jsonPath("$.data.whatsappEnabled").value(true));
    }

    @Test
    void deliveryWorkerRetriesAndEventuallySucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        NotificationDeliveryAdapter flakyInApp = new NotificationDeliveryAdapter() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.IN_APP;
            }

            @Override
            public DeliveryResult send(NotificationDeliveryRequest request) {
                if (attempts.incrementAndGet() < 2) {
                    return DeliveryResult.failed("TEMP_FAIL", "temporary failure");
                }
                return DeliveryResult.sent("in-app-retry-ok");
            }
        };

        UUID id = createDraftCoursework("Retry Work", daysFromNow(12));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", id).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        NotificationDelivery inApp = deliveryRepository.findAll().stream()
                .filter(d -> d.getChannel() == NotificationChannel.IN_APP)
                .findFirst()
                .orElseThrow();

        NotificationDeliveryWorker failingWorker = new NotificationDeliveryWorker(
                notificationProperties,
                deliveryRepository,
                messageResolver,
                List.of(flakyInApp),
                Clock.fixed(FIXED_NOW, ZONE));
        flushDeliveriesForWorker();
        failingWorker.processPendingBatch();

        NotificationDelivery afterFail = deliveryRepository.findById(inApp.getId()).orElseThrow();
        assertThat(afterFail.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(afterFail.getAttemptCount()).isEqualTo(1);

        NotificationDeliveryWorker retryWorker = new NotificationDeliveryWorker(
                notificationProperties,
                deliveryRepository,
                messageResolver,
                List.of(flakyInApp),
                Clock.fixed(FIXED_NOW.plus(Duration.ofMinutes(2)), ZONE));
        flushDeliveriesForWorker();
        retryWorker.processPendingBatch();

        NotificationDelivery afterSuccess = deliveryRepository.findById(inApp.getId()).orElseThrow();
        assertThat(afterSuccess.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(afterSuccess.getProviderMessageId()).isEqualTo("in-app-retry-ok");
    }

    private List<Notification> notificationsFor(UUID referenceId, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> type == n.getType())
                .filter(n -> referenceId.equals(n.getReferenceId()))
                .toList();
    }

    private UUID createDraftCoursework(String title, Instant dueAt) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/coursework")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseUnitId":"%s",
                                  "title":"%s",
                                  "description":"Orchestration test",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(courseUnit.getId(), title, dueAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(created, "$.data.id"));
    }

    private Coursework savePublishedCoursework(String title, Instant dueAt) {
        Coursework coursework = new Coursework(
                courseUnit,
                title,
                "Reminder test",
                null,
                CourseworkType.ASSIGNMENT,
                null,
                dueAt,
                null,
                CourseworkSourceType.DIRECT_ENTRY,
                null,
                null,
                admin);
        coursework.publish(FIXED_NOW);
        return courseworkRepository.saveAndFlush(coursework);
    }

    private Instant daysFromNow(int days) {
        return LocalDate.ofInstant(FIXED_NOW, ZONE).plusDays(days).atTime(17, 0).atZone(ZONE).toInstant();
    }

    private void flushDeliveriesForWorker() {
        jdbcTemplate.update(
                "update notification_deliveries set next_attempt_at = ? where status = 'PENDING'",
                java.sql.Timestamp.from(FIXED_NOW));
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

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock systemClock() {
            return Clock.fixed(FIXED_NOW, ZONE);
        }
    }
}
