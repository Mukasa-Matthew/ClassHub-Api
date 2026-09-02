package com.classhub.coursework;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseworkProgressService {

    private final CourseworkProgressRepository progressRepository;
    private final CourseworkRepository courseworkRepository;
    private final UserRepository userRepository;

    public CourseworkProgressService(
            CourseworkProgressRepository progressRepository,
            CourseworkRepository courseworkRepository,
            UserRepository userRepository) {
        this.progressRepository = progressRepository;
        this.courseworkRepository = courseworkRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CourseworkProgressResponse getOwnProgress(UUID courseworkId) {
        UUID studentId = requireStudentId();
        requirePublishedCoursework(courseworkId);
        return progressRepository
                .findByCourseworkIdAndStudentId(courseworkId, studentId)
                .map(CourseworkProgressResponse::from)
                .orElseGet(() -> CourseworkProgressResponse.notStarted(courseworkId));
    }

    @Transactional
    public CourseworkProgressResponse upsertOwnProgress(UUID courseworkId, UpdateCourseworkProgressRequest request) {
        if (request.status() == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_PROGRESS,
                    "status is required",
                    HttpStatus.BAD_REQUEST);
        }

        UUID studentId = requireStudentId();
        Coursework coursework = requirePublishedCoursework(courseworkId);
        User student = userRepository
                .findById(studentId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.UNAUTHENTICATED,
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED));

        Instant now = Instant.now();
        CourseworkProgress progress = progressRepository
                .findByCourseworkIdAndStudentId(courseworkId, studentId)
                .orElse(null);

        if (progress == null) {
            progress = new CourseworkProgress(coursework, student, request.status(), null);
            progress.applyStatus(request.status(), now);
            try {
                progress = progressRepository.saveAndFlush(progress);
            } catch (DataIntegrityViolationException ex) {
                progress = progressRepository
                        .findByCourseworkIdAndStudentId(courseworkId, studentId)
                        .orElseThrow(() -> new ApplicationException(
                                ErrorCodes.INVALID_COURSEWORK_PROGRESS,
                                "Could not save coursework progress",
                                HttpStatus.CONFLICT));
                progress.applyStatus(request.status(), now);
                progress = progressRepository.saveAndFlush(progress);
            }
        } else {
            progress.applyStatus(request.status(), now);
            progress = progressRepository.saveAndFlush(progress);
        }

        return CourseworkProgressResponse.from(progress);
    }

    @Transactional(readOnly = true)
    public CourseworkMyProgress resolveMyProgress(UUID courseworkId, UUID studentId) {
        return progressRepository
                .findByCourseworkIdAndStudentId(courseworkId, studentId)
                .map(CourseworkMyProgress::from)
                .orElseGet(CourseworkMyProgress::notStarted);
    }

    @Transactional(readOnly = true)
    public Map<UUID, CourseworkMyProgress> resolveMyProgress(Collection<UUID> courseworkIds, UUID studentId) {
        Map<UUID, CourseworkMyProgress> result = new HashMap<>();
        for (UUID id : courseworkIds) {
            result.put(id, CourseworkMyProgress.notStarted());
        }
        if (courseworkIds.isEmpty()) {
            return result;
        }
        List<CourseworkProgress> rows =
                progressRepository.findByStudentIdAndCourseworkIdIn(studentId, courseworkIds);
        for (CourseworkProgress row : rows) {
            result.put(row.getCoursework().getId(), CourseworkMyProgress.from(row));
        }
        return result;
    }

    private Coursework requirePublishedCoursework(UUID courseworkId) {
        Coursework coursework = courseworkRepository
                .findById(courseworkId)
                .orElseThrow(this::notFound);
        if (coursework.getStatus() != CourseworkStatus.PUBLISHED) {
            throw notFound();
        }
        return coursework;
    }

    private UUID requireStudentId() {
        ClassHubUserDetails principal = currentPrincipal();
        if (!principal.getRole().isStudentLike()) {
            throw new ApplicationException(
                    ErrorCodes.COURSEWORK_PROGRESS_NOT_ALLOWED,
                    "Only students may access coursework progress",
                    HttpStatus.FORBIDDEN);
        }
        return principal.getId();
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                ErrorCodes.COURSEWORK_NOT_FOUND,
                "Coursework not found",
                HttpStatus.NOT_FOUND);
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
}
