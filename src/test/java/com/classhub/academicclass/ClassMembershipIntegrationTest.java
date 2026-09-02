package com.classhub.academicclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
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
class ClassMembershipIntegrationTest {

    private static final String PASSWORD = "MembershipPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private AcademicClassRepository academicClassRepository;

    @Autowired
    private ClassMembershipRepository membershipRepository;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentSession;
    private User classRep;
    private User student;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        userService.create(new CreateUserCommand(
                "Super", "Admin", "mem.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        classRep = userService.create(new CreateUserCommand(
                "Class", "Rep", "mem.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        student = userService.create(new CreateUserCommand(
                "Stu", "Dent", "mem.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        adminSession = login("mem.admin@example.com");
        classRepSession = login("mem.rep@example.com");
        studentSession = login("mem.student@example.com");
    }

    @Test
    void adminCreatesClassWithSecureJoinCode() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/classes")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"BSIT Year 3",
                                  "programmeName":"  Bachelor of Science in IT  ",
                                  "programmeCode":"BSIT",
                                  "studyYear":3,
                                  "semester":1,
                                  "academicYear":2026
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.programmeName").value("Bachelor of Science in IT"))
                .andExpect(jsonPath("$.data.studyYear").value(3))
                .andExpect(jsonPath("$.data.semester").value(1))
                .andExpect(jsonPath("$.data.academicYear").value(2026))
                .andExpect(jsonPath("$.data.joinCode").isNotEmpty())
                .andReturn();

        String joinCode = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.joinCode");
        assertThat(joinCode).hasSize(6);
        AcademicClass persisted = academicClassRepository.findByJoinCodeIgnoreCase(joinCode).orElseThrow();
        assertThat(persisted.getProgrammeName()).isEqualTo("Bachelor of Science in IT");
        assertThat(persisted.getStudyYear()).isEqualTo(3);
        assertThat(persisted.getSemester()).isEqualTo(1);
        assertThat(persisted.getAcademicYear()).isEqualTo(2026);
    }

    @Test
    void existingClassIsSafelyBackfilledWithNormalizedStudyStructure() {
        AcademicClass existing = membershipTestSupport.defaultClass();

        assertThat(existing.getProgrammeName()).isEqualTo(existing.getName());
        assertThat(existing.getStudyYear()).isEqualTo(1);
        assertThat(existing.getSemester()).isEqualTo(1);
        assertThat(existing.getAcademicYear()).isBetween(1900, 2100);
    }

    @Test
    void adminCanPartiallyUpdateStudyStructureWithoutChangingClassIdentity() throws Exception {
        AcademicClass existing = membershipTestSupport.defaultClass();
        UUID classId = existing.getId();
        String joinCode = existing.getJoinCode();

        mockMvc.perform(patch("/api/v1/admin/classes/{id}", classId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programmeName":"  Software Engineering  ",
                                  "studyYear":4,
                                  "semester":2,
                                  "academicYear":2027
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(classId.toString()))
                .andExpect(jsonPath("$.data.programmeName").value("Software Engineering"))
                .andExpect(jsonPath("$.data.studyYear").value(4))
                .andExpect(jsonPath("$.data.semester").value(2))
                .andExpect(jsonPath("$.data.academicYear").value(2027))
                .andExpect(jsonPath("$.data.joinCode").value(joinCode));
    }

    @Test
    void classStudyStructureValidationRejectsBlankOrUnreasonableValues() throws Exception {
        assertInvalidClassCreate("", 1, 1, 2026);
        assertInvalidClassCreate("Programme", 0, 1, 2026);
        assertInvalidClassCreate("Programme", 1, 5, 2026);
        assertInvalidClassCreate("Programme", 1, 1, 2101);
    }

    @Test
    void publicRegistrationCreatesPendingStudentMembership() throws Exception {
        AcademicClass clazz = membershipTestSupport.defaultClass();
        String joinCode = clazz.getJoinCode();

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"New Student",
                                  "email":"new.student@example.com",
                                  "registrationNumber":"REG-1001",
                                  "phoneNumber":"+256770000003",
                                  "password":"%s",
                                  "classJoinCode":"%s"
                                }
                                """.formatted(PASSWORD, joinCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        User registered = userService.getByEmail("new.student@example.com");
        ClassMembership membership = membershipRepository
                .findByAcademicClassIdAndUserId(clazz.getId(), registered.getId())
                .orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.getMembershipRole()).isEqualTo(MembershipRole.STUDENT);
    }

    @Test
    void pendingStudentCannotAccessCourseUnits() throws Exception {
        AcademicClass clazz = membershipTestSupport.defaultClass();
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Pending Student",
                                  "email":"pending.student@example.com",
                                  "registrationNumber":"REG-1002",
                                  "phoneNumber":"+256770000004",
                                  "password":"%s",
                                  "classJoinCode":"%s"
                                }
                                """.formatted(PASSWORD, clazz.getJoinCode())))
                .andExpect(status().isOk());

        MockHttpSession pendingSession = login("pending.student@example.com");
        mockMvc.perform(get("/api/v1/course-units").session(pendingSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.CLASS_MEMBERSHIP_REQUIRED));

        mockMvc.perform(get("/api/v1/me/class-membership").session(pendingSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void classRepApprovesMemberAndClassListIncludesRep() throws Exception {
        AcademicClass clazz = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(clazz, classRep);

        userService.create(new CreateUserCommand(
                "Join", "Student", "join.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true, "REG-2001"));
        User joinStudent = userService.getByEmail("join.student@example.com");
        membershipRepository.saveAndFlush(new ClassMembership(
                clazz, joinStudent, MembershipRole.STUDENT, MembershipStatus.PENDING, java.time.Instant.now()));

        UUID membershipId = membershipRepository
                .findByAcademicClassIdAndUserId(clazz.getId(), joinStudent.getId())
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/approve", membershipId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/class-rep/class-list").session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[?(@.membershipRole=='CLASS_REP')]").exists())
                .andExpect(jsonPath("$.data.members[?(@.registrationNumber=='REG-2001')]").exists());
    }

    @Test
    void classRepCannotApproveOtherClassMembership() throws Exception {
        AcademicClass classA = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(classA, classRep);

        MvcResult classB = mockMvc.perform(post("/api/v1/admin/classes")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Class B",
                                  "programmeName":"Programme B",
                                  "programmeCode":"CLB",
                                  "studyYear":2,
                                  "semester":2,
                                  "academicYear":2026
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID classBId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                classB.getResponse().getContentAsString(), "$.data.id"));

        User otherStudent = userService.create(new CreateUserCommand(
                "Other", "Student", "other.class@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true, "REG-3001"));
        AcademicClass clazzB = academicClassRepository.findById(classBId).orElseThrow();
        ClassMembership pending = membershipRepository.saveAndFlush(new ClassMembership(
                clazzB, otherStudent, MembershipRole.STUDENT, MembershipStatus.PENDING, java.time.Instant.now()));

        mockMvc.perform(post("/api/v1/class-rep/members/{id}/approve", pending.getId())
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());
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
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void assertInvalidClassCreate(
            String programmeName, int studyYear, int semester, int academicYear) throws Exception {
        mockMvc.perform(post("/api/v1/admin/classes")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Validation Class",
                                  "programmeName":"%s",
                                  "studyYear":%d,
                                  "semester":%d,
                                  "academicYear":%d
                                }
                                """.formatted(programmeName, studyYear, semester, academicYear)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.VALIDATION_ERROR));
    }
}
