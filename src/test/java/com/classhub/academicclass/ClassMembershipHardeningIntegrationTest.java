package com.classhub.academicclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditLogRepository;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.coursework.CourseworkProgressRepository;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.notification.NotificationRepository;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class ClassMembershipHardeningIntegrationTest {

    private static final String PASSWORD = "MembershipHardening1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AcademicClassRepository classRepository;
    @Autowired private ClassMembershipRepository membershipRepository;
    @Autowired private CourseUnitRepository courseUnitRepository;
    @Autowired private CourseworkProgressRepository progressRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JoinCodeGenerator joinCodeGenerator;
    @Autowired private ClassMembershipTestSupport membershipSupport;
    @Autowired private LoginRateLimiter loginRateLimiter;
    @Autowired private AuditLogRepository auditLogRepository;

    private AcademicClass classA;
    private AcademicClass classB;
    private User repA;
    private User repB;
    private User studentA;
    private User studentB;
    private MockHttpSession repASession;
    private MockHttpSession repBSession;
    private MockHttpSession studentASession;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        repA = createUser("Alice", "Representative", "hard.rep.a@example.com", UserRole.CLASS_REP, null);
        repB = createUser("Bob", "Representative", "hard.rep.b@example.com", UserRole.CLASS_REP, null);
        studentA = createUser("Amy", "Zulu", "hard.student.a@example.com", UserRole.STUDENT, "REG-A1");
        studentB = createUser("Ben", "Young", "hard.student.b@example.com", UserRole.STUDENT, "REG-B1");

        classA = membershipSupport.defaultClass();
        classB = classRepository.saveAndFlush(new AcademicClass(
                "Second Class", "Second Programme", "SCP", joinCodeGenerator.generateUnique(classRepository),
                AcademicClassStatus.ACTIVE));
        membershipSupport.activateClassRep(classA, repA);
        membershipSupport.activateClassRep(classB, repB);
        membershipSupport.activateStudent(classA, studentA);
        membershipSupport.activateStudent(classB, studentB);

        repASession = login(repA.getEmail());
        repBSession = login(repB.getEmail());
        studentASession = login(studentA.getEmail());
    }

    @Test
    void rejectionIsClassScopedStateSafeAndAudited() throws Exception {
        User pendingUser = createUser("Pending", "Member", "hard.pending@example.com", UserRole.STUDENT, "REG-P1");
        ClassMembership pending = pending(classA, pendingUser);

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reject", pending.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipStatus").value("REJECTED"));

        assertThat(membershipRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.REJECTED);
        assertThat(auditLogRepository.search(AuditAction.CLASS_MEMBER_REJECTED, repA.getId(), null,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);

        MockHttpSession rejectedSession = login(pendingUser.getEmail());
        mockMvc.perform(get("/api/v1/course-units").session(rejectedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.CLASS_MEMBERSHIP_REQUIRED));

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reject", pending.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_CLASS_MEMBERSHIP_STATE));

        ClassMembership otherClassPending = pending(classB,
                createUser("Other", "Pending", "hard.other.pending@example.com", UserRole.STUDENT, "REG-P2"));
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reject", otherClassPending.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reject", otherClassPending.getId())
                        .session(studentASession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivationRevokesAccessAndProtectsClassRepsAndClassBoundaries() throws Exception {
        ClassMembership active = membershipRepository
                .findByAcademicClassIdAndUserId(classA.getId(), studentA.getId()).orElseThrow();
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", active.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipStatus").value("INACTIVE"));
        mockMvc.perform(get("/api/v1/course-units").session(studentASession))
                .andExpect(status().isForbidden());
        assertThat(auditLogRepository.search(AuditAction.CLASS_MEMBER_DEACTIVATED, repA.getId(), null,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", active.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isConflict());

        ClassMembership repMembership = membershipRepository
                .findByAcademicClassIdAndUserId(classA.getId(), repA.getId()).orElseThrow();
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", repMembership.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isForbidden());

        ClassMembership studentBMembership = membershipRepository
                .findByAcademicClassIdAndUserId(classB.getId(), studentB.getId()).orElseThrow();
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", studentBMembership.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", studentBMembership.getId())
                        .session(studentASession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reactivationRestoresInactiveStudentAccessAndIsClassScoped() throws Exception {
        ClassMembership active = membershipRepository
                .findByAcademicClassIdAndUserId(classA.getId(), studentA.getId()).orElseThrow();
        UUID membershipId = active.getId();

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", membershipId)
                        .session(repASession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipStatus").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/course-units").session(studentASession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", membershipId)
                        .session(repASession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.approvedAt").isNotEmpty());

        ClassMembership reactivated = membershipRepository.findById(membershipId).orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(membershipId);
        assertThat(reactivated.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reactivated.getApprovedBy().getId()).isEqualTo(repA.getId());

        mockMvc.perform(get("/api/v1/me/class-membership").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/course-units").session(studentASession))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.search(AuditAction.CLASS_MEMBER_REACTIVATED, repA.getId(), null,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", membershipId)
                        .session(repASession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_CLASS_MEMBERSHIP_STATE));

        ClassMembership pending = pending(classA,
                createUser("Reactivate", "Pending", "hard.reactivate.pending@example.com", UserRole.STUDENT, "REG-R1"));
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", pending.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isConflict());

        ClassMembership rejected = pending(classA,
                createUser("Reactivate", "Rejected", "hard.reactivate.rejected@example.com", UserRole.STUDENT, "REG-R2"));
        rejected.reject();
        membershipRepository.saveAndFlush(rejected);
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", rejected.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isConflict());

        ClassMembership repMembership = membershipRepository
                .findByAcademicClassIdAndUserId(classA.getId(), repA.getId()).orElseThrow();
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", repMembership.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isForbidden());

        ClassMembership inactiveB = membershipRepository
                .findByAcademicClassIdAndUserId(classB.getId(), studentB.getId()).orElseThrow();
        inactiveB.deactivate();
        membershipRepository.saveAndFlush(inactiveB);
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", inactiveB.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/reactivate", membershipId)
                        .session(studentASession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void regeneratedJoinCodeInvalidatesOldCodeAndAcceptsCaseInsensitiveNewCode() throws Exception {
        String oldCode = classA.getJoinCode();
        MvcResult result = mockMvc.perform(post("/api/v1/class-rep/class/join-code/regenerate")
                        .session(repASession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinCode").value(org.hamcrest.Matchers.matchesPattern("[A-HJ-NP-Z2-9]{6}")))
                .andReturn();
        String newCode = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.joinCode");
        assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(newCode).doesNotContain(classA.getId().toString().substring(0, 6));

        register("Old Code", "hard.old.code@example.com", "REG-J1", oldCode.toLowerCase(), status().isBadRequest());
        register("New Code", "hard.new.code@example.com", "REG-J2", newCode.toLowerCase(), status().isOk());
        User joined = userService.getByEmail("hard.new.code@example.com");
        assertThat(membershipRepository.findByAcademicClassIdAndUserId(classA.getId(), joined.getId()))
                .get().extracting(ClassMembership::getStatus).isEqualTo(MembershipStatus.PENDING);
        assertThat(auditLogRepository.search(AuditAction.CLASS_JOIN_CODE_REGENERATED, repA.getId(), null,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);

        String classBCode = classB.getJoinCode();
        mockMvc.perform(post("/api/v1/class-rep/class/join-code/regenerate")
                        .session(repASession).with(csrf()))
                .andExpect(status().isOk());
        assertThat(classRepository.findById(classB.getId()).orElseThrow().getJoinCode()).isEqualTo(classBCode);
        mockMvc.perform(post("/api/v1/class-rep/class/join-code/regenerate")
                        .session(studentASession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void classListContainsOnlyActiveOwnClassMembersInDeterministicSafeOrder() throws Exception {
        User first = createUser("Aaron", "Alpha", "hard.alpha@example.com", UserRole.STUDENT, "REG-L1");
        User second = createUser("Zoe", "Alpha", "hard.zoe@example.com", UserRole.STUDENT, "REG-L2");
        membershipSupport.activateStudent(classA, second);
        membershipSupport.activateStudent(classA, first);
        ClassMembership rejected = pending(classA,
                createUser("Rejected", "Person", "hard.rejected@example.com", UserRole.STUDENT, "REG-L3"));
        rejected.reject();
        membershipRepository.saveAndFlush(rejected);
        pending(classA, createUser("Waiting", "Person", "hard.waiting@example.com", UserRole.STUDENT, "REG-L4"));

        mockMvc.perform(get("/api/v1/class-rep/class-list").session(repASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].fullName").value("Aaron Alpha"))
                .andExpect(jsonPath("$.data.members[1].fullName").value("Zoe Alpha"))
                .andExpect(jsonPath("$.data.members[?(@.email=='hard.rep.a@example.com')]").exists())
                .andExpect(jsonPath("$.data.members[?(@.email=='hard.rejected@example.com')]").doesNotExist())
                .andExpect(jsonPath("$.data.members[?(@.email=='hard.waiting@example.com')]").doesNotExist())
                .andExpect(jsonPath("$.data.members[?(@.email=='hard.student.b@example.com')]").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].passwordHash").doesNotExist());
    }

    @Test
    void registrationNumberAndExistingUserJoinRulesAreEnforced() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Missing Number","email":"hard.no.reg@example.com",
                                 "password":"%s","classJoinCode":"%s"}
                                """.formatted(PASSWORD, classA.getJoinCode())))
                .andExpect(status().isBadRequest());

        register("Trim Number", "hard.trim.reg@example.com", "  Reg-Trim  ", classA.getJoinCode(), status().isOk());
        assertThat(userService.getByEmail("hard.trim.reg@example.com").getRegistrationNumber()).isEqualTo("Reg-Trim");
        register("Duplicate Number", "hard.duplicate.reg@example.com", "reg-trim", classA.getJoinCode(), status().isConflict());

        User existing = createUser("Existing", "Student", "hard.existing@example.com", UserRole.STUDENT, "REG-E1");
        MockHttpSession existingSession = login(existing.getEmail());
        mockMvc.perform(post("/api/v1/classes/join").session(existingSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"%s\"}".formatted(classA.getJoinCode().toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.membershipRole").value("STUDENT"));
        mockMvc.perform(post("/api/v1/classes/join").session(existingSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"%s\"}".formatted(classA.getJoinCode())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/classes/join").session(repBSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"%s\"}".formatted(classA.getJoinCode())))
                .andExpect(status().isForbidden());

        classB.updateDetails(classB.getName(), classB.getProgrammeName(), classB.getProgrammeCode(), AcademicClassStatus.INACTIVE);
        classRepository.saveAndFlush(classB);
        mockMvc.perform(post("/api/v1/classes/join").session(existingSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"%s\"}".formatted(classB.getJoinCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_JOIN_CODE));
    }

    @Test
    void classRepCanUseOwnStudentResourcesButCannotReadAnotherStudentsNotes() throws Exception {
        var unit = courseUnitRepository.saveAndFlush(membershipSupport.newCourseUnit(
                classA, "CU-998101", "HARD 1", "Hardening", "hardening", null, null, true));

        mockMvc.perform(get("/api/v1/dashboard/student").session(repASession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications").session(repASession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/notification-preferences").session(repASession))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/notification-preferences").session(repASession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"whatsappEnabled\":true}"))
                .andExpect(status().isOk());

        MvcResult coursework = mockMvc.perform(post("/api/v1/coursework").session(repASession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseUnitId":"%s","title":"Rep coursework","description":"For progress",
                                 "type":"ASSIGNMENT","dueAt":"%s","sourceType":"DIRECT_ENTRY"}
                                """.formatted(unit.getId(), Instant.now().plus(10, ChronoUnit.DAYS))))
                .andExpect(status().isCreated()).andReturn();
        UUID courseworkId = UUID.fromString(json(coursework, "$.data.id"));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", courseworkId).session(repASession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/coursework").session(repASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='%s')]".formatted(courseworkId)).exists());
        mockMvc.perform(put("/api/v1/coursework/{id}/progress", courseworkId)
                        .session(repASession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        assertThat(progressRepository.findByCourseworkIdAndStudentId(courseworkId, repA.getId())).isPresent();

        MvcResult ownNote = mockMvc.perform(post("/api/v1/notes").session(repASession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseUnitId":"%s","title":"Rep note","rawContent":"Private rep content"}
                                """.formatted(unit.getId())))
                .andExpect(status().isCreated()).andReturn();
        UUID ownNoteId = UUID.fromString(json(ownNote, "$.data.id"));
        mockMvc.perform(patch("/api/v1/notes/{id}", ownNoteId).session(repASession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated rep note\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notes/{id}/complete", ownNoteId).session(repASession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", ownNoteId).session(repASession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isOk());

        MvcResult studentNote = mockMvc.perform(post("/api/v1/notes").session(studentASession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseUnitId":"%s","title":"Student note","rawContent":"Student private content"}
                                """.formatted(unit.getId())))
                .andExpect(status().isCreated()).andReturn();
        UUID studentNoteId = UUID.fromString(json(studentNote, "$.data.id"));
        mockMvc.perform(get("/api/v1/notes/{id}", studentNoteId).session(repASession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/notes/{id}/ai/outputs", studentNoteId).session(repASession))
                .andExpect(status().isNotFound());
    }

    @Test
    void pendingRejectedAndInactiveMembershipsCannotUseAcademicResources() throws Exception {
        User pendingUser = createUser("Gate", "Pending", "hard.gate.pending@example.com", UserRole.STUDENT, "REG-G1");
        pending(classA, pendingUser);
        assertResourceDenied(pendingUser);

        User rejectedUser = createUser("Gate", "Rejected", "hard.gate.rejected@example.com", UserRole.STUDENT, "REG-G2");
        ClassMembership rejected = pending(classA, rejectedUser);
        rejected.reject();
        membershipRepository.saveAndFlush(rejected);
        assertResourceDenied(rejectedUser);

        User inactiveUser = createUser("Gate", "Inactive", "hard.gate.inactive@example.com", UserRole.STUDENT, "REG-G3");
        ClassMembership inactive = membershipSupport.activateStudent(classA, inactiveUser);
        inactive.deactivate();
        membershipRepository.saveAndFlush(inactive);
        assertResourceDenied(inactiveUser);

        mockMvc.perform(get("/api/v1/course-units").session(studentASession)).andExpect(status().isOk());
    }

    @Test
    void twoClassMemberManagementAndAcademicDataAreIsolated() throws Exception {
        ClassMembership pendingB = pending(classB,
                createUser("Class B", "Pending", "hard.b.pending@example.com", UserRole.STUDENT, "REG-B2"));
        ClassMembership activeB = membershipRepository
                .findByAcademicClassIdAndUserId(classB.getId(), studentB.getId()).orElseThrow();

        mockMvc.perform(get("/api/v1/class-rep/members").session(repASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='hard.student.b@example.com')]").doesNotExist());
        for (String action : new String[] {"approve", "reject"}) {
            mockMvc.perform(post("/api/v1/class-rep/members/{id}/" + action, pendingB.getId())
                            .session(repASession).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/class-rep/members/{id}/deactivate", activeB.getId())
                        .session(repASession).with(csrf()))
                .andExpect(status().isForbidden());

        courseUnitRepository.saveAndFlush(membershipSupport.newCourseUnit(
                classA, "CU-998201", "A-ONLY", "Class A Unit", "class a unit", null, null, true));
        courseUnitRepository.saveAndFlush(membershipSupport.newCourseUnit(
                classB, "CU-998202", "B-ONLY", "Class B Unit", "class b unit", null, null, true));
        mockMvc.perform(get("/api/v1/course-units").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='A-ONLY')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='B-ONLY')]").doesNotExist());
    }

    @Test
    void classScopedPublicationNotifiesOnlyActiveMembersOfTheOwningClass() throws Exception {
        User rejectedUser = createUser(
                "No", "Notice", "hard.no.notice@example.com", UserRole.STUDENT, "REG-N1");
        ClassMembership rejected = pending(classA, rejectedUser);
        rejected.reject();
        membershipRepository.saveAndFlush(rejected);

        UUID announcementA = createAndPublishAnnouncement(repASession, "Class A notice");
        UUID announcementB = createAndPublishAnnouncement(repBSession, "Class B notice");

        assertThat(notificationReferences(studentA.getId())).contains(announcementA).doesNotContain(announcementB);
        assertThat(notificationReferences(studentB.getId())).contains(announcementB).doesNotContain(announcementA);
        assertThat(notificationReferences(repA.getId())).doesNotContain(announcementB);
        assertThat(notificationReferences(rejectedUser.getId())).doesNotContain(announcementA);
    }

    private User createUser(String first, String last, String email, UserRole role, String registrationNumber) {
        return userService.create(new CreateUserCommand(
                first, last, email, null, PASSWORD, role, UserStatus.ACTIVE, true, registrationNumber));
    }

    private ClassMembership pending(AcademicClass academicClass, User user) {
        return membershipRepository.saveAndFlush(new ClassMembership(
                academicClass, user, MembershipRole.STUDENT, MembershipStatus.PENDING, Instant.now()));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void register(
            String fullName,
            String email,
            String registrationNumber,
            String joinCode,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","registrationNumber":"%s",
                                 "password":"%s","classJoinCode":"%s"}
                                """.formatted(fullName, email, registrationNumber, PASSWORD, joinCode)))
                .andExpect(expectedStatus);
    }

    private void assertResourceDenied(User user) throws Exception {
        MockHttpSession session = login(user.getEmail());
        mockMvc.perform(get("/api/v1/course-units").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.CLASS_MEMBERSHIP_REQUIRED));
        mockMvc.perform(get("/api/v1/announcements").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/notes").session(session))
                .andExpect(status().isForbidden());
    }

    private UUID createAndPublishAnnouncement(MockHttpSession session, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/announcements").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"content\":\"Scoped announcement content\"}"
                                .formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json(created, "$.data.id"));
        mockMvc.perform(post("/api/v1/announcements/{id}/publish", id).session(session).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private java.util.List<UUID> notificationReferences(UUID recipientId) {
        return notificationRepository.findInbox(recipientId, null, PageRequest.of(0, 100)).stream()
                .map(notification -> notification.getReferenceId())
                .toList();
    }

    private static String json(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path).toString();
    }
}
