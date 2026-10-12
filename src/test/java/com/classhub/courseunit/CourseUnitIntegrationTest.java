package com.classhub.courseunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.sql.Timestamp;
import java.time.Instant;
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
class CourseUnitIntegrationTest {

    private static final String PASSWORD = "CourseUnitPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

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

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();

        userService.create(new CreateUserCommand(
                "Super", "Admin", "cu.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "cu.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "cu.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("cu.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("cu.student@example.com"));

        adminSession = login("cu.admin@example.com");
        classRepSession = login("cu.rep@example.com");
        studentSession = login("cu.student@example.com");
    }

    @Test
    void superAdminAndClassRepCanCreateStudentCannot() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classId":"%s",
                                  "code":"BIT 3204",
                                  "name":"  Cyber   Security  ",
                                  "lecturerName":"Dr Example",
                                  "description":"Cyber Security course unit"
                                }
                                """.formatted(defaultClass.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Cyber Security"))
                .andExpect(jsonPath("$.data.code").value("BIT 3204"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.normalizedName").doesNotExist());

        CourseUnit stored = courseUnitRepository
                .findByAcademicClassIdAndNormalizedName(defaultClass.getId(), "cyber security")
                .orElseThrow();
        assertThat(stored.getName()).isEqualTo("Cyber Security");
        assertThat(stored.getNormalizedName()).isEqualTo("cyber security");

        mockMvc.perform(post("/api/v1/course-units")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Scientific Writing","lecturerName":"Prof Writer"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Scientific Writing"));

        mockMvc.perform(post("/api/v1/course-units")
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Fail"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateNormalizedNameRejectedAtServiceAndDatabase() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"IT Research\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"  it   research  \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_ALREADY_EXISTS));

        Timestamp now = Timestamp.from(Instant.now());
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into course_units
                        (id, academic_class_id, internal_code, code, name, normalized_name, lecturer_name, description, active, created_at, updated_at)
                        values (?, ?, ?, null, ?, ?, null, null, true, ?, ?)
                        """,
                        UUID.randomUUID(),
                        defaultClass.getId(),
                        "CU-990099",
                        "IT Research",
                        "it research",
                        now,
                        now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listingAndGetRespectRoleVisibility() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"Systems Automation\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"Inactive Unit\"}"))
                .andExpect(status().isCreated());

        UUID activeId = courseUnitRepository
                .findByAcademicClassIdAndNormalizedName(defaultClass.getId(), "systems automation")
                .orElseThrow()
                .getId();
        UUID inactiveId = courseUnitRepository
                .findByAcademicClassIdAndNormalizedName(defaultClass.getId(), "inactive unit")
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/v1/course-units/{id}/status", inactiveId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/v1/course-units").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/course-units").session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/course-units").session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Systems Automation"));

        mockMvc.perform(get("/api/v1/course-units").session(studentSession).param("active", "false"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/course-units/{id}", activeId).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Systems Automation"));

        mockMvc.perform(get("/api/v1/course-units/{id}", inactiveId).session(studentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/course-units/{id}", inactiveId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void updatesRenameAndStatusRules() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"E-Commerce\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"Other Unit\"}"))
                .andExpect(status().isCreated());

        UUID id = courseUnitRepository
                .findByAcademicClassIdAndNormalizedName(defaultClass.getId(), "e-commerce")
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/v1/course-units/{id}", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"  E-Commerce,   E-Business  ",
                                  "lecturerName":"Dr Biz",
                                  "description":"Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E-Commerce, E-Business"));

        assertThat(courseUnitRepository.findById(id).orElseThrow().getNormalizedName())
                .isEqualTo("e-commerce, e-business");

        mockMvc.perform(patch("/api/v1/course-units/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other Unit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_ALREADY_EXISTS));

        mockMvc.perform(patch("/api/v1/course-units/{id}", id)
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/course-units/{id}/status", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(patch("/api/v1/course-units/{id}/status", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(patch("/api/v1/course-units/{id}/status", id)
                        .session(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/course-units/{id}/status", id)
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/course-units/{id}", UUID.randomUUID()).session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_NOT_FOUND));
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
