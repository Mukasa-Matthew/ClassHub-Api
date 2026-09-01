package com.classhub.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.classhub.academicclass.AcademicClass;
import com.classhub.ai.LocalAiNoteProcessor;
import com.classhub.auth.LoginRateLimiter;
import com.classhub.common.api.ErrorCodes;
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
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
class LectureNoteIntegrationTest {

    private static final String PASSWORD = "NotesPass1!";
    private static final String RAW =
            "social engineering uses people instead of directly attacking systems. phishing is one example.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private LectureNoteRepository noteRepository;

    @Autowired
    private LectureNoteAiOutputRepository aiOutputRepository;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass defaultClass;
    private MockHttpSession adminSession;
    private MockHttpSession classRepSession;
    private MockHttpSession studentASession;
    private MockHttpSession studentBSession;
    private CourseUnit cyber;
    private CourseUnit research;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.reset();
        userService.create(new CreateUserCommand(
                "Super", "Admin", "note.admin@example.com", null, PASSWORD,
                UserRole.SUPER_ADMIN, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Class", "Rep", "note.rep@example.com", null, PASSWORD,
                UserRole.CLASS_REP, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "A", "note.student.a@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));
        userService.create(new CreateUserCommand(
                "Stu", "B", "note.student.b@example.com", null, PASSWORD,
                UserRole.STUDENT, UserStatus.ACTIVE, true));

        defaultClass = membershipTestSupport.defaultClass();
        membershipTestSupport.activateClassRep(defaultClass, userService.getByEmail("note.rep@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("note.student.a@example.com"));
        membershipTestSupport.activateStudent(defaultClass, userService.getByEmail("note.student.b@example.com"));

        cyber = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-997001", "BIT 3204", "Cyber Security", "cyber security", null, null, true));
        research = courseUnitRepository.saveAndFlush(
                membershipTestSupport.newCourseUnit("CU-997002", null, "IT Research", "it research", null, null, true));

        adminSession = login("note.admin@example.com");
        classRepSession = login("note.rep@example.com");
        studentASession = login("note.student.a@example.com");
        studentBSession = login("note.student.b@example.com");
    }

    @Test
    void noteLifecycleOwnershipAndFilters() throws Exception {
        mockMvc.perform(post("/api/v1/notes")
                        .session(classRepSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(cyber.getId(), "Rep private note", RAW)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/notes")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(cyber.getId(), "Nope", RAW)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/notes")
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(cyber.getId(), "Lecture 4 - Social Engineering", RAW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.rawContent").value(RAW))
                .andExpect(jsonPath("$.data.aiOutputCount").value(0))
                .andReturn();
        UUID noteId = UUID.fromString(json(created, "$.data.id"));

        mockMvc.perform(get("/api/v1/notes/{id}", noteId).session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(noteId.toString()));

        mockMvc.perform(get("/api/v1/notes/{id}", noteId).session(studentBSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.LECTURE_NOTE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/notes/{id}", noteId).session(adminSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/notes")
                        .session(studentBSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(research.getId(), "B note", "other student note")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notes").session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(noteId.toString()));

        mockMvc.perform(get("/api/v1/notes")
                        .session(studentASession)
                        .param("courseUnitId", research.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/notes")
                        .session(studentASession)
                        .param("courseUnitId", cyber.getId().toString())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/v1/notes/{id}", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawContent":"%s lecturer said attackers use urgency"}
                                """.formatted(RAW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawContent")
                        .value(RAW + " lecturer said attackers use urgency"));

        mockMvc.perform(post("/api/v1/notes/{id}/complete", noteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.lectureEndedAt").isNotEmpty());

        mockMvc.perform(patch("/api/v1/notes/{id}", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cannot edit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_LECTURE_NOTE_STATE));

        mockMvc.perform(post("/api/v1/notes/{id}/complete", noteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/notes/{id}/archive", noteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(patch("/api/v1/notes/{id}", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Still no\"}"))
                .andExpect(status().isConflict());

        MvcResult activeForArchive = mockMvc.perform(post("/api/v1/notes")
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(cyber.getId(), "Active archive", RAW)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID activeArchiveId = UUID.fromString(json(activeForArchive, "$.data.id"));
        mockMvc.perform(post("/api/v1/notes/{id}/archive", activeArchiveId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        assertThat(noteRepository.findById(noteId).orElseThrow().getRawContent())
                .isEqualTo(RAW + " lecturer said attackers use urgency");
    }

    @Test
    void aiProcessingPreservesRawContentAndHistory() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/notes")
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(cyber.getId(), "AI lecture", RAW)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID noteId = UUID.fromString(json(created, "$.data.id"));

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.INVALID_LECTURE_NOTE_STATE));

        mockMvc.perform(post("/api/v1/notes/{id}/complete", noteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk());

        String rawBefore = noteRepository.findById(noteId).orElseThrow().getRawContent();

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation").value("ORGANIZE"))
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.modelProvider").value(LocalAiNoteProcessor.PROVIDER));

        assertThat(noteRepository.findById(noteId).orElseThrow().getRawContent()).isEqualTo(rawBefore);
        assertThat(aiOutputRepository.countByLectureNoteId(noteId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"SUMMARIZE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation").value("SUMMARIZE"));

        assertThat(noteRepository.findById(noteId).orElseThrow().getRawContent()).isEqualTo(rawBefore);
        assertThat(aiOutputRepository.countByLectureNoteId(noteId)).isEqualTo(2);

        mockMvc.perform(get("/api/v1/notes/{id}/ai/outputs", noteId).session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].operation").value("SUMMARIZE"))
                .andExpect(jsonPath("$.data[1].operation").value("ORGANIZE"));

        mockMvc.perform(get("/api/v1/notes/{id}", noteId).session(studentASession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiOutputCount").value(2))
                .andExpect(jsonPath("$.data.rawContent").value(rawBefore));

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(studentBSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/notes/{id}/ai/outputs", noteId).session(studentBSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notes/{id}/ai/outputs", noteId).session(classRepSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"NOT_A_REAL_OP\"}"))
                .andExpect(status().isBadRequest());

        MvcResult failNote = mockMvc.perform(post("/api/v1/notes")
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(
                                cyber.getId(),
                                "Fail note",
                                RAW + " " + LocalAiNoteProcessor.FORCE_FAILURE_MARKER)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID failId = UUID.fromString(json(failNote, "$.data.id"));
        mockMvc.perform(post("/api/v1/notes/{id}/complete", failId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk());
        String failRawBefore = noteRepository.findById(failId).orElseThrow().getRawContent();

        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", failId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value(ErrorCodes.AI_NOTE_PROCESSING_FAILED));

        assertThat(aiOutputRepository.countByLectureNoteId(failId)).isZero();
        assertThat(noteRepository.findById(failId).orElseThrow().getRawContent()).isEqualTo(failRawBefore);

        mockMvc.perform(post("/api/v1/notes/{id}/archive", noteId)
                        .session(studentASession)
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notes/{id}/ai/process", noteId)
                        .session(studentASession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"ORGANIZE\"}"))
                .andExpect(status().isConflict());
    }

    private static String createBody(UUID courseUnitId, String title, String rawContent) {
        return """
                {
                  "courseUnitId":"%s",
                  "title":"%s",
                  "rawContent":"%s",
                  "lectureStartedAt":"2026-08-31T08:00:00Z"
                }
                """.formatted(courseUnitId, title, rawContent);
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
