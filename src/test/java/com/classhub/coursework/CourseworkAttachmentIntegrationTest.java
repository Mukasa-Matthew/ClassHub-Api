package com.classhub.coursework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.storage.LocalFileStorage;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.nio.charset.StandardCharsets;
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
class CourseworkAttachmentIntegrationTest {

    private static final String PASSWORD = "AttachPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private CourseworkAttachmentRepository attachmentRepository;

    @Autowired
    private LocalFileStorage localFileStorage;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentSession;
    private CourseUnit courseUnit;
    private UUID draftId;
    private UUID otherDraftId;
    private UUID publishedId;
    private UUID cancelledId;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        userService.create(new CreateUserCommand(
                "Super", "Admin", "att.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "att.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "Dent", "att.student@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("att.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("att.student@example.com"));

        courseUnit = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-995001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));

        adminSession = login("att.admin@example.com");
        classRepSession = login("att.rep@example.com");
        studentSession = login("att.student@example.com");

        Instant due = Instant.now().plus(10, ChronoUnit.DAYS);
        draftId = createDraft("Draft With Files", due);
        otherDraftId = createDraft("Other Draft", due);
        publishedId = createDraft("Published With Files", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", publishedId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
        cancelledId = createDraft("Cancelled With Files", due);
        mockMvc.perform(post("/api/v1/coursework/{id}/cancel", cancelledId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void uploadValidationAuthAndStorage() throws Exception {
        MockMultipartFile pdf = pdfFile("handout.pdf", "Assignment brief content");

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(pdf)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFileName").value("handout.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.courseworkId").value(draftId.toString()))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn();

        UUID attachmentId = UUID.fromString(json(uploaded, "$.data.id"));
        CourseworkAttachment stored = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(stored.getStorageKey()).isNotEqualTo("handout.pdf");
        assertThat(stored.getStorageKey()).endsWith(".pdf");
        assertThat(localFileStorage.exists(stored.getStorageKey())).isTrue();
        assertThat(localFileStorage.root().toAbsolutePath().normalize().toString())
                .doesNotContain("src/main/resources");

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(pdfFile("admin.pdf", "Admin upload"))
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(pdf)
                        .session(studentSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", publishedId)
                        .file(pdf)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", cancelledId)
                        .file(pdf)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(new MockMultipartFile(
                                "file",
                                "malware.exe",
                                "application/octet-stream",
                                new byte[] {0x4d, 0x5a}))
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_ATTACHMENT));

        byte[] oversized = new byte[(int) (1024 * 1024) + 16];
        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", oversized))
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.ATTACHMENT_TOO_LARGE));

        mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(new MockMultipartFile(
                                "file",
                                "../etc/passwd.pdf",
                                "application/pdf",
                                "safe".getBytes(StandardCharsets.UTF_8)))
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFileName").value("passwd.pdf"));
    }

    @Test
    void listDownloadDeleteAndCrossCourseworkIsolation() throws Exception {
        MockMultipartFile pdf = pdfFile("brief.pdf", "Published brief body");

        MvcResult draftUpload = mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", draftId)
                        .file(pdf)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID draftAttachmentId = UUID.fromString(json(draftUpload, "$.data.id"));
        String draftStorageKey =
                attachmentRepository.findById(draftAttachmentId).orElseThrow().getStorageKey();

        mockMvc.perform(get("/api/v1/coursework/{id}/attachments", draftId).session(classRepSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/coursework/{id}/attachments", draftId).session(studentSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/coursework/{id}/attachments/{aid}/download", draftId, draftAttachmentId)
                        .session(studentSession))
                .andExpect(status().isNotFound());

        // Attach to a still-draft item, then publish that coursework for student access.
        UUID publishableId = createDraft("Will Publish With Attachment", Instant.now().plus(12, ChronoUnit.DAYS));
        MvcResult publishedUpload = mockMvc.perform(multipart("/api/v1/coursework/{id}/attachments", publishableId)
                        .file(pdfFile("student.pdf", "Student visible content"))
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID publishedAttachmentId = UUID.fromString(json(publishedUpload, "$.data.id"));
        mockMvc.perform(post("/api/v1/coursework/{id}/publish", publishableId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coursework/{id}/attachments", publishableId).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalFileName").value("student.pdf"));

        MvcResult download = mockMvc.perform(get(
                                "/api/v1/coursework/{id}/attachments/{aid}/download",
                                publishableId,
                                publishedAttachmentId)
                        .session(studentSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("student.pdf")))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray())
                .isEqualTo("Student visible content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get(
                                "/api/v1/coursework/{id}/attachments/{aid}/download",
                                otherDraftId,
                                publishedAttachmentId)
                        .session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.ATTACHMENT_NOT_FOUND));

        mockMvc.perform(get(
                                "/api/v1/coursework/{id}/attachments/{aid}/download",
                                publishableId,
                                UUID.randomUUID())
                        .session(adminSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/coursework/{id}/attachments/{aid}", draftId, draftAttachmentId)
                        .session(studentSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                                "/api/v1/coursework/{id}/attachments/{aid}",
                                publishableId,
                                publishedAttachmentId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/coursework/{id}/attachments/{aid}", draftId, draftAttachmentId)
                        .session(classRepSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(attachmentRepository.findById(draftAttachmentId)).isEmpty();
        assertThat(localFileStorage.exists(draftStorageKey)).isFalse();

        mockMvc.perform(get("/api/v1/coursework/{id}", publishableId).session(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentCount").value(0));
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
                                  "description":"Attachment test coursework",
                                  "type":"ASSIGNMENT",
                                  "dueAt":"%s",
                                  "sourceType":"DIRECT_ENTRY"
                                }
                                """.formatted(courseUnit.getId(), title, due)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(created, "$.data.id"));
    }

    private static MockMultipartFile pdfFile(String name, String body) {
        return new MockMultipartFile(
                "file", name, "application/pdf", body.getBytes(StandardCharsets.UTF_8));
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
