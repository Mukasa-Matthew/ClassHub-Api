package com.classhub.note;

import com.classhub.ai.AiNoteOperation;
import com.classhub.ai.AiNoteProcessor;
import com.classhub.ai.AiNoteRequest;
import com.classhub.ai.AiNoteResult;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.api.Pagination;
import com.classhub.common.exception.ApplicationException;
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LectureNoteService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final LectureNoteRepository noteRepository;
    private final LectureNoteAiOutputRepository aiOutputRepository;
    private final CourseUnitRepository courseUnitRepository;
    private final UserRepository userRepository;
    private final AiNoteProcessor aiNoteProcessor;

    public LectureNoteService(
            LectureNoteRepository noteRepository,
            LectureNoteAiOutputRepository aiOutputRepository,
            CourseUnitRepository courseUnitRepository,
            UserRepository userRepository,
            AiNoteProcessor aiNoteProcessor) {
        this.noteRepository = noteRepository;
        this.aiOutputRepository = aiOutputRepository;
        this.courseUnitRepository = courseUnitRepository;
        this.userRepository = userRepository;
        this.aiNoteProcessor = aiNoteProcessor;
    }

    @Transactional
    public LectureNoteResponse create(CreateLectureNoteRequest request) {
        UUID studentId = requireStudentId();
        CourseUnit courseUnit = requireCourseUnit(request.courseUnitId());
        String rawContent = requireText(request.rawContent(), "rawContent");
        String title = normalizeOptional(request.title());

        LectureNote note = new LectureNote(
                requireStudent(studentId),
                courseUnit,
                title,
                rawContent,
                request.lectureStartedAt());
        LectureNote saved = noteRepository.saveAndFlush(note);
        return LectureNoteResponse.from(saved, 0L);
    }

    @Transactional
    public LectureNoteResponse update(UUID id, UpdateLectureNoteRequest request) {
        LectureNote note = requireOwnedNote(id);
        if (note.getStatus() != LectureNoteStatus.ACTIVE) {
            throw invalidState("Only ACTIVE notes can be updated");
        }

        CourseUnit courseUnit = request.courseUnitId() == null
                ? note.getCourseUnit()
                : requireCourseUnit(request.courseUnitId());
        String title = request.title() == null ? note.getTitle() : normalizeOptional(request.title());
        String rawContent = request.rawContent() == null
                ? note.getRawContent()
                : requireText(request.rawContent(), "rawContent");
        Instant startedAt =
                request.lectureStartedAt() == null ? note.getLectureStartedAt() : request.lectureStartedAt();

        note.updateDetails(courseUnit, title, rawContent, startedAt);
        LectureNote saved = noteRepository.saveAndFlush(note);
        return LectureNoteResponse.from(saved, aiOutputRepository.countByLectureNoteId(saved.getId()));
    }

    @Transactional
    public LectureNoteResponse complete(UUID id) {
        LectureNote note = requireOwnedNote(id);
        if (note.getStatus() != LectureNoteStatus.ACTIVE) {
            throw invalidState("Only ACTIVE notes can be completed");
        }
        note.complete(Instant.now());
        LectureNote saved = noteRepository.saveAndFlush(note);
        return LectureNoteResponse.from(saved, aiOutputRepository.countByLectureNoteId(saved.getId()));
    }

    @Transactional
    public LectureNoteResponse archive(UUID id) {
        LectureNote note = requireOwnedNote(id);
        if (note.getStatus() == LectureNoteStatus.ARCHIVED) {
            throw invalidState("Note is already archived");
        }
        note.archive();
        LectureNote saved = noteRepository.saveAndFlush(note);
        return LectureNoteResponse.from(saved, aiOutputRepository.countByLectureNoteId(saved.getId()));
    }

    @Transactional(readOnly = true)
    public LectureNoteResponse getById(UUID id) {
        LectureNote note = requireOwnedNote(id);
        return LectureNoteResponse.from(note, aiOutputRepository.countByLectureNoteId(note.getId()));
    }

    @Transactional(readOnly = true)
    public ApiPage list(UUID courseUnitId, LectureNoteStatus status, Integer page, Integer size) {
        UUID studentId = requireStudentId();
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 1) {
            throw invalidData("page must be >= 1");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw invalidData("size must be between 1 and " + MAX_SIZE);
        }

        Page<LectureNote> result = noteRepository.searchOwned(
                studentId, courseUnitId, status, PageRequest.of(pageNumber - 1, pageSize));
        List<UUID> ids = result.getContent().stream().map(LectureNote::getId).toList();
        Map<UUID, Long> counts = countsFor(ids);
        List<LectureNoteResponse> data = result.getContent().stream()
                .map(note -> LectureNoteResponse.from(note, counts.getOrDefault(note.getId(), 0L)))
                .toList();
        Pagination pagination = new Pagination(
                pageNumber, pageSize, result.getTotalElements(), result.getTotalPages());
        return new ApiPage(data, pagination);
    }

    @Transactional
    public LectureNoteAiOutputResponse processAi(UUID id, ProcessLectureNoteRequest request) {
        if (request.operation() == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_AI_NOTE_OPERATION,
                    "operation is required",
                    HttpStatus.BAD_REQUEST);
        }

        LectureNote note = requireOwnedNote(id);
        if (note.getStatus() != LectureNoteStatus.COMPLETED) {
            throw invalidState("Only COMPLETED notes can be AI processed");
        }

        String originalRaw = note.getRawContent();
        AiNoteResult result;
        try {
            result = aiNoteProcessor.process(new AiNoteRequest(
                    originalRaw, note.getCourseUnit().getName(), request.operation()));
        } catch (RuntimeException ex) {
            throw new ApplicationException(
                    ErrorCodes.AI_NOTE_PROCESSING_FAILED,
                    "AI note processing failed",
                    HttpStatus.BAD_GATEWAY);
        }

        if (result == null || result.content() == null || result.content().isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.AI_NOTE_PROCESSING_FAILED,
                    "AI note processing failed",
                    HttpStatus.BAD_GATEWAY);
        }

        // Guardrail: raw content must remain unchanged after processing.
        if (!originalRaw.equals(note.getRawContent())) {
            throw new ApplicationException(
                    ErrorCodes.AI_NOTE_PROCESSING_FAILED,
                    "AI note processing failed",
                    HttpStatus.BAD_GATEWAY);
        }

        LectureNoteAiOutput output = new LectureNoteAiOutput(
                note,
                request.operation(),
                result.content().trim(),
                result.modelProvider(),
                result.modelName());
        return LectureNoteAiOutputResponse.from(aiOutputRepository.saveAndFlush(output));
    }

    @Transactional(readOnly = true)
    public List<LectureNoteAiOutputResponse> listAiOutputs(UUID id) {
        requireOwnedNote(id);
        return aiOutputRepository.findByLectureNoteIdOrderByCreatedAtDesc(id).stream()
                .map(LectureNoteAiOutputResponse::from)
                .toList();
    }

    private Map<UUID, Long> countsFor(List<UUID> noteIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (UUID noteId : noteIds) {
            counts.put(noteId, 0L);
        }
        if (noteIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : aiOutputRepository.countGroupedByNoteId(noteIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private LectureNote requireOwnedNote(UUID id) {
        UUID studentId = requireStudentId();
        return noteRepository
                .findOwnedDetailedById(id, studentId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.LECTURE_NOTE_NOT_FOUND,
                        "Lecture note not found",
                        HttpStatus.NOT_FOUND));
    }

    private CourseUnit requireCourseUnit(UUID courseUnitId) {
        return courseUnitRepository
                .findById(courseUnitId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.COURSE_UNIT_NOT_FOUND,
                        "Course unit not found",
                        HttpStatus.NOT_FOUND));
    }

    private User requireStudent(UUID studentId) {
        return userRepository
                .findById(studentId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.UNAUTHENTICATED,
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED));
    }

    private UUID requireStudentId() {
        ClassHubUserDetails principal = currentPrincipal();
        if (!principal.getRole().isStudentLike()) {
            throw new ApplicationException(
                    ErrorCodes.FORBIDDEN,
                    "Only students may access lecture notes",
                    HttpStatus.FORBIDDEN);
        }
        return principal.getId();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalidData(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw invalidData(field + " is required");
        }
        return trimmed;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApplicationException invalidData(String message) {
        return new ApplicationException(
                ErrorCodes.INVALID_LECTURE_NOTE_DATA, message, HttpStatus.BAD_REQUEST);
    }

    private static ApplicationException invalidState(String message) {
        return new ApplicationException(
                ErrorCodes.INVALID_LECTURE_NOTE_STATE, message, HttpStatus.CONFLICT);
    }

    private static ClassHubUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }

    public record ApiPage(List<LectureNoteResponse> data, Pagination pagination) {
    }
}
