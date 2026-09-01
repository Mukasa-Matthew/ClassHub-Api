package com.classhub.academicclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditLogRepository;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, SemesterTimelineIntegrationTest.FixedClockConfig.class})
@Transactional
class SemesterTimelineIntegrationTest {

    private static final String PASSWORD = "SemesterTimeline1!";
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 9, 15);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AcademicClassRepository classRepository;
    @Autowired private ClassMembershipRepository membershipRepository;
    @Autowired private ClassMembershipTestSupport membershipSupport;
    @Autowired private LoginRateLimiter loginRateLimiter;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JoinCodeGenerator joinCodeGenerator;

    private AcademicClass classA;
    private AcademicClass classB;
    private User repA;
    private User repB;
    private User studentA;
    private MockHttpSession repASession;
    private MockHttpSession repBSession;
    private MockHttpSession studentASession;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        repA = createUser("Rep", "Alpha", "sem.rep.a@example.com", UserRole.CLASS_REP, null);
        repB = createUser("Rep", "Beta", "sem.rep.b@example.com", UserRole.CLASS_REP, null);
        studentA = createUser("Stu", "Alpha", "sem.student.a@example.com", UserRole.STUDENT, "SEM-A1");

        classA = membershipSupport.defaultClass();
        classB = classRepository.saveAndFlush(new AcademicClass(
                "Second Class",
                "Second Programme",
                "SCP",
                joinCodeGenerator.generateUnique(classRepository),
                AcademicClassStatus.ACTIVE));

        membershipSupport.activateClassRep(classA, repA);
        membershipSupport.activateClassRep(classB, repB);
        membershipSupport.activateStudent(classA, studentA);

        repASession = login(repA.getEmail());
        repBSession = login(repB.getEmail());
        studentASession = login(studentA.getEmail());
    }

    @Test
    void classRepConfiguresAndUpdatesSemesterTimelineOnSameClassRow() throws Exception {
        mockMvc.perform(get("/api/v1/me/semester").session(repASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.className").value(classA.getName()));

        UUID classId = classA.getId();
        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterName":"Year 3 Semester 1",
                                  "startDate":"2026-08-25",
                                  "endDate":"2026-12-12"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semesterName").value("Year 3 Semester 1"))
                .andExpect(jsonPath("$.data.startDate").value("2026-08-25"))
                .andExpect(jsonPath("$.data.endDate").value("2026-12-12"))
                .andExpect(jsonPath("$.data.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.totalDays").value(110))
                .andExpect(jsonPath("$.data.elapsedDays").value(22))
                .andExpect(jsonPath("$.data.remainingDays").value(88))
                .andExpect(jsonPath("$.data.progressPercentage").value(20.00));

        AcademicClass persisted = classRepository.findById(classId).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(classId);
        assertThat(persisted.getSemesterName()).isEqualTo("Year 3 Semester 1");
        assertThat(persisted.getSemesterStartDate()).isEqualTo(LocalDate.of(2026, 8, 25));

        assertThat(auditLogRepository.search(
                        AuditAction.CLASS_SEMESTER_TIMELINE_UPDATED, repA.getId(), null, PageRequest.of(0, 10))
                .getTotalElements())
                .isEqualTo(1);

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterName":"Updated Semester",
                                  "startDate":"2026-09-01",
                                  "endDate":"2026-12-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semesterName").value("Updated Semester"));

        assertThat(classRepository.findById(classId).orElseThrow().getSemesterName()).isEqualTo("Updated Semester");
        assertThat(classRepository.findAll()).hasSize(2);
    }

    @Test
    void studentCanReadTimelineButCannotConfigureIt() throws Exception {
        configureSemester(repASession);

        mockMvc.perform(get("/api/v1/me/semester").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.className").value(classA.getName()))
                .andExpect(jsonPath("$.data.joinCode").doesNotExist());

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Nope","startDate":"2026-08-25","endDate":"2026-12-12"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void classRepChangesApplyOnlyToOwnClass() throws Exception {
        configureSemester(repASession);

        AcademicClass classBBefore = classRepository.findById(classB.getId()).orElseThrow();
        assertThat(classBBefore.isSemesterTimelineConfigured()).isFalse();

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repBSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterName":"Class B Semester",
                                  "startDate":"2026-01-01",
                                  "endDate":"2026-04-01"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(classRepository.findById(classA.getId()).orElseThrow().getSemesterName())
                .isEqualTo("Year 3 Semester 1");
        assertThat(classRepository.findById(classB.getId()).orElseThrow().getSemesterName())
                .isEqualTo("Class B Semester");
    }

    @Test
    void inactivePendingAndRejectedMembershipsCannotReadTimeline() throws Exception {
        configureSemester(repASession);

        User pending = createUser("Pen", "Ding", "sem.pending@example.com", UserRole.STUDENT, "SEM-P1");
        membershipRepository.saveAndFlush(new ClassMembership(
                classA, pending, MembershipRole.STUDENT, MembershipStatus.PENDING, Instant.now()));
        assertMembershipDenied(login(pending.getEmail()));

        User rejected = createUser("Rej", "Ected", "sem.rejected@example.com", UserRole.STUDENT, "SEM-R1");
        ClassMembership rejectedMembership = new ClassMembership(
                classA, rejected, MembershipRole.STUDENT, MembershipStatus.PENDING, Instant.now());
        rejectedMembership.reject();
        membershipRepository.saveAndFlush(rejectedMembership);
        assertMembershipDenied(login(rejected.getEmail()));

        ClassMembership inactiveMembership =
                membershipRepository.findByAcademicClassIdAndUserId(classA.getId(), studentA.getId()).orElseThrow();
        inactiveMembership.deactivate();
        membershipRepository.saveAndFlush(inactiveMembership);
        assertMembershipDenied(studentASession);
    }

    @Test
    void validationRejectsIncompleteOrInvalidDateRanges() throws Exception {
        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Bad","startDate":"2026-08-25"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_CLASS_DATA));

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Bad","endDate":"2026-12-12"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Bad","startDate":"2026-12-12","endDate":"2026-08-25"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Bad","startDate":"2026-08-25","endDate":"2026-08-25"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsPastCurrentAndFutureSemesterDates() throws Exception {
        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Past","startDate":"2025-01-01","endDate":"2025-06-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("COMPLETED"));

        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Future","startDate":"2027-01-01","endDate":"2027-06-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("UPCOMING"));
    }

    @Test
    void timelineStatesRespectDateBoundaries() throws Exception {
        putSemester("2026-08-25", "2026-12-12");
        assertState("IN_PROGRESS");

        putSemester("2026-09-16", "2026-12-12");
        assertState("UPCOMING");

        putSemester("2026-08-25", "2026-09-15");
        assertState("IN_PROGRESS");
        mockMvc.perform(get("/api/v1/me/semester").session(repASession))
                .andExpect(jsonPath("$.data.elapsedDays").value(22))
                .andExpect(jsonPath("$.data.remainingDays").value(0))
                .andExpect(jsonPath("$.data.progressPercentage").value(100.00));

        putSemester("2026-08-25", "2026-09-14");
        assertState("COMPLETED");
    }

    private void configureSemester(MockHttpSession session) throws Exception {
        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterName":"Year 3 Semester 1",
                                  "startDate":"2026-08-25",
                                  "endDate":"2026-12-12"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void putSemester(String startDate, String endDate) throws Exception {
        mockMvc.perform(put("/api/v1/class-rep/class/semester")
                        .session(repASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"semesterName":"Boundary Test","startDate":"%s","endDate":"%s"}
                                """.formatted(startDate, endDate)))
                .andExpect(status().isOk());
    }

    private void assertState(String state) throws Exception {
        mockMvc.perform(get("/api/v1/me/semester").session(repASession))
                .andExpect(jsonPath("$.data.state").value(state));
    }

    private void assertMembershipDenied(MockHttpSession session) throws Exception {
        mockMvc.perform(get("/api/v1/me/semester").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.CLASS_MEMBERSHIP_REQUIRED));
    }

    private User createUser(String first, String last, String email, UserRole role, String registrationNumber) {
        return userService.create(new CreateUserCommand(
                first, last, email, null, PASSWORD, role, UserStatus.ACTIVE, true, registrationNumber));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock systemClock() {
            return Clock.fixed(
                    FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }
}
