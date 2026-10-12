package com.classhub.courseunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
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
class CourseUnitInternalCodeAndCoverIntegrationTest {

    private static final String PASSWORD = "CuCoverPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private CourseUnitInternalCodeGenerator internalCodeGenerator;

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
                "Super", "Admin", "cuc.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "cuc.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "cuc.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("cuc.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("cuc.student@example.com"));

        adminSession = login("cuc.admin@example.com");
        classRepSession = login("cuc.rep@example.com");
        studentSession = login("cuc.student@example.com");
    }

    @Test
    void internalCodeGeneratedAndVisibleOnlyToSuperAdmin() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/course-units")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BSIT3104","name":"Cyber Security"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("BSIT3104"))
                .andExpect(jsonPath("$.data.internalCode").doesNotExist())
                .andReturn();

        UUID id = UUID.fromString(json(created, "$.data.id"));
        CourseUnit stored = courseUnitRepository.findById(id).orElseThrow();
        assertThat(stored.getInternalCode()).matches("CU-\\d{6}");
        assertThat(stored.getCode()).isEqualTo("BSIT3104");

        mockMvc.perform(get("/api/v1/course-units/{id}", id).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalCode").doesNotExist())
                .andExpect(jsonPath("$.data.code").value("BSIT3104"));

        mockMvc.perform(get("/api/v1/course-units/{id}", id).session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalCode").doesNotExist());

        mockMvc.perform(get("/api/v1/course-units/{id}", id).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalCode").value(stored.getInternalCode()));
    }

    @Test
    void internalCodesAreUniqueAcrossCreates() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"Unit Alpha\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"Unit Beta\"}"))
                .andExpect(status().isCreated());

        assertThat(courseUnitRepository.findAll().stream().map(CourseUnit::getInternalCode).distinct().count())
                .isEqualTo(courseUnitRepository.count());
    }

    @Test
    void internalCodeCannotBeSetOrUpdatedByClients() throws Exception {
        mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"Immutable Code\",\"internalCode\":\"CU-999999\"}"))
                .andExpect(status().isCreated());

        UUID id = courseUnitRepository
                .findByAcademicClassIdAndNormalizedName(defaultClass.getId(), "immutable code")
                .orElseThrow()
                .getId();
        String original = courseUnitRepository.findById(id).orElseThrow().getInternalCode();

        mockMvc.perform(patch("/api/v1/course-units/{id}", id)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"internalCode\":\"CU-888888\"}"))
                .andExpect(status().isOk());

        assertThat(courseUnitRepository.findById(id).orElseThrow().getInternalCode()).isEqualTo(original);
    }

    @Test
    void coverImageUploadViewReplaceRemovePermissions() throws Exception {
        UUID id = createCourseUnit("Networking");

        MockMultipartFile image = pngImage("cover.png", 200, 150);
        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", id)
                        .file(image)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasCoverImage").value(true))
                .andExpect(jsonPath("$.data.coverImageUrl").value("/api/v1/course-units/" + id + "/cover-image"));

        mockMvc.perform(get("/api/v1/course-units/{id}/cover-image", id).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        MockMultipartFile replacement = pngImage("new-cover.png", 300, 200);
        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", id)
                        .file(replacement)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasCoverImage").value(true));

        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", id)
                        .file(image)
                        .session(studentSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/course-units/{id}/cover-image", id)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/course-units/{id}/cover-image", id).session(studentSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_COVER_NOT_FOUND));
    }

    @Test
    void coverImageRejectsInvalidFiles() throws Exception {
        UUID id = createCourseUnit("Database Systems");

        MockMultipartFile executable = new MockMultipartFile(
                "file", "../../evil.exe", "application/octet-stream", "MZ".getBytes());
        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", id)
                        .file(executable)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ATTACHMENT));

        MockMultipartFile svg = new MockMultipartFile(
                "file", "cover.svg", "image/svg+xml", "<svg></svg>".getBytes());
        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", id)
                        .file(svg)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        byte[] largePng = pngBytes(100, 100);
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "huge.png", "image/png", new byte[(int) (6 * 1024 * 1024)]);
        oversized.getBytes()[0] = (byte) 0x89;
        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", id)
                        .file(oversized)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.ATTACHMENT_TOO_LARGE));

        mockMvc.perform(multipart("/api/v1/course-units/{id}/cover-image", UUID.randomUUID())
                        .file(new MockMultipartFile("file", "cover.png", "image/png", largePng))
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.COURSE_UNIT_NOT_FOUND));
    }

    @Test
    void internalCodeGeneratorFormatsSequenceValues() {
        assertThat(CourseUnitInternalCodeGenerator.format(1)).isEqualTo("CU-000001");
        assertThat(CourseUnitInternalCodeGenerator.format(123)).isEqualTo("CU-000123");
        assertThat(internalCodeGenerator.nextCode()).matches("CU-\\d{6}");
    }

    private UUID createCourseUnit(String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/course-units")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classId\":\"" + defaultClass.getId() + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(created, "$.data.id"));
    }

    private static MockMultipartFile pngImage(String filename, int width, int height) throws Exception {
        return new MockMultipartFile("file", filename, "image/png", pngBytes(width, height));
    }

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
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
