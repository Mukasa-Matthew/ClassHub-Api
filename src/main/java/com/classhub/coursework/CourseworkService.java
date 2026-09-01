package com.classhub.coursework;

import com.classhub.academicclass.ClassMembershipAccessService;
import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.api.Pagination;
import com.classhub.common.exception.ApplicationException;
import com.classhub.courseunit.CourseUnit;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.notification.NotificationOrchestrator;
import com.classhub.notification.NotificationService;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseworkService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("0.01");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("100");
    private static final Set<CourseworkSourceType> SOURCES_REQUIRING_URL = EnumSet.of(
            CourseworkSourceType.MOODLE,
            CourseworkSourceType.CANVAS,
            CourseworkSourceType.BLACKBOARD,
            CourseworkSourceType.GOOGLE_CLASSROOM,
            CourseworkSourceType.GOOGLE_DRIVE,
            CourseworkSourceType.EXTERNAL_LINK);

    private final CourseworkRepository courseworkRepository;
    private final CourseUnitRepository courseUnitRepository;
    private final UserRepository userRepository;
    private final CourseworkProgressService progressService;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final CourseworkAttachmentService attachmentService;
    private final AuditService auditService;
    private final ClassMembershipAccessService membershipAccessService;

    public CourseworkService(
            CourseworkRepository courseworkRepository,
            CourseUnitRepository courseUnitRepository,
            UserRepository userRepository,
            CourseworkProgressService progressService,
            NotificationService notificationService,
            NotificationOrchestrator notificationOrchestrator,
            CourseworkAttachmentService attachmentService,
            AuditService auditService,
            ClassMembershipAccessService membershipAccessService) {
        this.courseworkRepository = courseworkRepository;
        this.courseUnitRepository = courseUnitRepository;
        this.userRepository = userRepository;
        this.progressService = progressService;
        this.notificationService = notificationService;
        this.notificationOrchestrator = notificationOrchestrator;
        this.attachmentService = attachmentService;
        this.auditService = auditService;
        this.membershipAccessService = membershipAccessService;
    }

    @Transactional
    public CourseworkResponse create(CreateCourseworkRequest request) {
        CourseUnit courseUnit = requireCourseUnit(request.courseUnitId());
        enforceCourseUnitManagementAccess(courseUnit);
        String title = requireText(request.title(), "title");
        String description = requireText(request.description(), "description");
        String instructions = normalizeOptionalText(request.instructions());
        Instant dueAt = requireDueAt(request.dueAt(), true);
        Instant issuedAt = request.issuedAt();
        validateDeadlineOrdering(issuedAt, dueAt);
        BigDecimal weight = normalizeWeight(request.weight());
        String sourceUrl = normalizeSourceUrl(request.sourceType(), request.sourceUrl());
        String sourceLabel = normalizeOptionalText(request.sourceLabel());

        Coursework coursework = new Coursework(
                courseUnit,
                title,
                description,
                instructions,
                request.type(),
                issuedAt,
                dueAt,
                weight,
                request.sourceType(),
                sourceUrl,
                sourceLabel,
                requireCurrentUser());

        Coursework saved = courseworkRepository.saveAndFlush(coursework);
        auditService.record(
                AuditAction.COURSEWORK_CREATED,
                AuditEntityTypes.COURSEWORK,
                saved.getId(),
                "Created draft coursework \"" + saved.getTitle() + "\"");
        return CourseworkResponse.from(saved);
    }

    @Transactional
    public CourseworkResponse update(UUID id, UpdateCourseworkRequest request) {
        Coursework coursework = requireCoursework(id);
        enforceCourseworkManagementAccess(coursework);
        if (coursework.getStatus() == CourseworkStatus.DRAFT) {
            return updateDraft(coursework, request);
        }
        if (coursework.getStatus() == CourseworkStatus.PUBLISHED) {
            return updatePublished(coursework, request);
        }
        throw invalidState("Only DRAFT or PUBLISHED coursework can be updated");
    }

    private CourseworkResponse updateDraft(Coursework coursework, UpdateCourseworkRequest request) {
        CourseUnit courseUnit = request.courseUnitId() == null
                ? coursework.getCourseUnit()
                : requireCourseUnit(request.courseUnitId());
        String title = request.title() == null
                ? coursework.getTitle()
                : requireText(request.title(), "title");
        String description = request.description() == null
                ? coursework.getDescription()
                : requireText(request.description(), "description");
        String instructions = request.instructions() == null
                ? coursework.getInstructions()
                : normalizeOptionalText(request.instructions());
        CourseworkType type = request.type() == null ? coursework.getType() : request.type();
        Instant issuedAt = request.issuedAt() == null ? coursework.getIssuedAt() : request.issuedAt();
        Instant dueAt = request.dueAt() == null
                ? coursework.getDueAt()
                : requireDueAt(request.dueAt(), true);
        validateDeadlineOrdering(issuedAt, dueAt);
        BigDecimal weight = request.weight() == null
                ? coursework.getWeight()
                : normalizeWeight(request.weight());
        CourseworkSourceType sourceType =
                request.sourceType() == null ? coursework.getSourceType() : request.sourceType();
        String sourceUrl = request.sourceUrl() == null && request.sourceType() == null
                ? coursework.getSourceUrl()
                : normalizeSourceUrl(
                        sourceType,
                        request.sourceUrl() == null ? coursework.getSourceUrl() : request.sourceUrl());
        String sourceLabel = request.sourceLabel() == null
                ? coursework.getSourceLabel()
                : normalizeOptionalText(request.sourceLabel());

        coursework.updateDetails(
                courseUnit,
                title,
                description,
                instructions,
                type,
                issuedAt,
                dueAt,
                weight,
                sourceType,
                sourceUrl,
                sourceLabel);

        Coursework saved = courseworkRepository.saveAndFlush(coursework);
        auditService.record(
                AuditAction.COURSEWORK_UPDATED,
                AuditEntityTypes.COURSEWORK,
                saved.getId(),
                "Updated draft coursework \"" + saved.getTitle() + "\"");
        return CourseworkResponse.from(saved);
    }

    private CourseworkResponse updatePublished(Coursework coursework, UpdateCourseworkRequest request) {
        rejectPublishedCourseUnitChange(coursework, request);

        CourseUnit courseUnit = coursework.getCourseUnit();
        String title = request.title() == null
                ? coursework.getTitle()
                : requireText(request.title(), "title");
        String description = request.description() == null
                ? coursework.getDescription()
                : requireText(request.description(), "description");
        String instructions = request.instructions() == null
                ? coursework.getInstructions()
                : normalizeOptionalText(request.instructions());
        CourseworkType type = request.type() == null ? coursework.getType() : request.type();
        Instant issuedAt = request.issuedAt() == null ? coursework.getIssuedAt() : request.issuedAt();
        Instant oldDueAt = coursework.getDueAt();
        Instant newDueAt = request.dueAt() == null ? oldDueAt : requireDueAt(request.dueAt(), false);
        validateDeadlineOrdering(issuedAt, newDueAt);
        BigDecimal weight = request.weight() == null
                ? coursework.getWeight()
                : normalizeWeight(request.weight());
        CourseworkSourceType sourceType =
                request.sourceType() == null ? coursework.getSourceType() : request.sourceType();
        String sourceUrl = request.sourceUrl() == null && request.sourceType() == null
                ? coursework.getSourceUrl()
                : normalizeSourceUrl(
                        sourceType,
                        request.sourceUrl() == null ? coursework.getSourceUrl() : request.sourceUrl());
        String sourceLabel = request.sourceLabel() == null
                ? coursework.getSourceLabel()
                : normalizeOptionalText(request.sourceLabel());

        String oldInstructions = coursework.getInstructions();

        coursework.updateDetails(
                courseUnit,
                title,
                description,
                instructions,
                type,
                issuedAt,
                newDueAt,
                weight,
                sourceType,
                sourceUrl,
                sourceLabel);

        Coursework saved = courseworkRepository.saveAndFlush(coursework);

        boolean deadlineChanged = !oldDueAt.equals(newDueAt);
        boolean instructionsChanged = instructionsMeaningfullyChanged(oldInstructions, instructions);

        if (deadlineChanged) {
            notificationOrchestrator.onCourseworkDeadlineChanged(saved, oldDueAt, newDueAt);
        }
        if (instructionsChanged && Boolean.TRUE.equals(request.notifyStudentsOfUpdate())) {
            notificationOrchestrator.onCourseworkInstructionsUpdated(saved);
        }

        auditService.record(
                AuditAction.COURSEWORK_UPDATED,
                AuditEntityTypes.COURSEWORK,
                saved.getId(),
                "Updated published coursework \"" + saved.getTitle() + "\"");

        return CourseworkResponse.from(saved);
    }

    private void rejectPublishedCourseUnitChange(Coursework coursework, UpdateCourseworkRequest request) {
        if (request.courseUnitId() != null
                && !request.courseUnitId().equals(coursework.getCourseUnit().getId())) {
            throw invalidPublishedField("courseUnitId");
        }
    }

    private static ApplicationException invalidPublishedField(String field) {
        return new ApplicationException(
                ErrorCodes.INVALID_COURSEWORK_STATE,
                field + " cannot be changed on published coursework",
                HttpStatus.CONFLICT);
    }

    private static boolean instructionsMeaningfullyChanged(String before, String after) {
        return !normalizeComparableText(before).equals(normalizeComparableText(after));
    }

    private static String normalizeComparableText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    @Transactional
    public CourseworkResponse publish(UUID id) {
        Coursework coursework = requireCoursework(id);
        enforceCourseworkManagementAccess(coursework);
        if (coursework.getStatus() != CourseworkStatus.DRAFT) {
            throw invalidState("Only DRAFT coursework can be published");
        }
        validatePublishable(coursework);
        coursework.publish(Instant.now());
        Coursework saved = courseworkRepository.saveAndFlush(coursework);
        notificationService.notifyCourseworkPublished(saved);
        auditService.record(
                AuditAction.COURSEWORK_PUBLISHED,
                AuditEntityTypes.COURSEWORK,
                saved.getId(),
                "Published coursework \"" + saved.getTitle() + "\"");
        return CourseworkResponse.from(saved);
    }

    @Transactional
    public CourseworkResponse cancel(UUID id) {
        Coursework coursework = requireCoursework(id);
        enforceCourseworkManagementAccess(coursework);
        CourseworkStatus previousStatus = coursework.getStatus();
        if (previousStatus != CourseworkStatus.DRAFT && previousStatus != CourseworkStatus.PUBLISHED) {
            throw invalidState("Only DRAFT or PUBLISHED coursework can be cancelled");
        }
        coursework.cancel();
        Coursework saved = courseworkRepository.saveAndFlush(coursework);
        if (previousStatus == CourseworkStatus.PUBLISHED) {
            notificationOrchestrator.onCourseworkCancelled(saved);
        }
        auditService.record(
                AuditAction.COURSEWORK_CANCELLED,
                AuditEntityTypes.COURSEWORK,
                saved.getId(),
                "Cancelled coursework \"" + saved.getTitle() + "\"");
        return CourseworkResponse.from(saved);
    }

    @Transactional
    public CourseworkResponse archive(UUID id) {
        Coursework coursework = requireCoursework(id);
        enforceCourseworkManagementAccess(coursework);
        if (coursework.getStatus() != CourseworkStatus.PUBLISHED) {
            throw invalidState("Only PUBLISHED coursework can be archived");
        }
        coursework.archive();
        Coursework saved = courseworkRepository.saveAndFlush(coursework);
        auditService.record(
                AuditAction.COURSEWORK_ARCHIVED,
                AuditEntityTypes.COURSEWORK,
                saved.getId(),
                "Archived coursework \"" + saved.getTitle() + "\"");
        return CourseworkResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CourseworkResponse getById(UUID id) {
        Coursework coursework = courseworkRepository.findDetailedById(id).orElseThrow(this::notFound);
        if (currentRole() == UserRole.STUDENT && coursework.getStatus() != CourseworkStatus.PUBLISHED) {
            throw notFound();
        }
        enforceCourseworkReadAccess(coursework);
        return toResponse(coursework);
    }

    @Transactional(readOnly = true)
    public ApiPage list(
            UUID courseUnitId,
            CourseworkStatus status,
            Boolean upcoming,
            Boolean overdue,
            Integer page,
            Integer size) {
        if (Boolean.TRUE.equals(upcoming) && Boolean.TRUE.equals(overdue)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DATA,
                    "upcoming and overdue cannot both be true",
                    HttpStatus.BAD_REQUEST);
        }

        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 1) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DATA,
                    "page must be >= 1",
                    HttpStatus.BAD_REQUEST);
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DATA,
                    "size must be between 1 and " + MAX_SIZE,
                    HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        CourseworkStatus effectiveStatus = status;
        boolean filterDueAfter = false;
        boolean filterDueBefore = false;
        Instant dueAfter = now;
        Instant dueBefore = now;

        UserRole role = currentRole();
        UUID classIdFilter = null;
        if (role == UserRole.STUDENT) {
            effectiveStatus = CourseworkStatus.PUBLISHED;
            classIdFilter = membershipAccessService.requireActiveClassId(currentPrincipal().getId());
        } else if (role == UserRole.CLASS_REP) {
            classIdFilter = membershipAccessService.requireClassRepMembership(currentPrincipal().getId())
                    .getAcademicClass()
                    .getId();
        }

        if (Boolean.TRUE.equals(upcoming)) {
            effectiveStatus = CourseworkStatus.PUBLISHED;
            filterDueAfter = true;
            dueAfter = now;
        } else if (Boolean.TRUE.equals(overdue)) {
            effectiveStatus = CourseworkStatus.PUBLISHED;
            filterDueBefore = true;
            dueBefore = now;
        }

        Page<Coursework> result = courseworkRepository.search(
                courseUnitId,
                classIdFilter,
                effectiveStatus,
                filterDueAfter,
                dueAfter,
                filterDueBefore,
                dueBefore,
                PageRequest.of(pageNumber - 1, pageSize));

        List<CourseworkResponse> data;
        List<UUID> ids = result.getContent().stream().map(Coursework::getId).toList();
        var counts = attachmentService.countsFor(ids);
        if (role == UserRole.STUDENT) {
            UUID studentId = currentPrincipal().getId();
            var progressById = progressService.resolveMyProgress(ids, studentId);
            data = result.getContent().stream()
                    .map(item -> CourseworkResponse.from(
                            item,
                            counts.getOrDefault(item.getId(), 0L),
                            progressById.get(item.getId())))
                    .toList();
        } else {
            data = result.getContent().stream()
                    .map(item -> CourseworkResponse.from(
                            item, counts.getOrDefault(item.getId(), 0L), null))
                    .toList();
        }

        Pagination pagination = new Pagination(
                pageNumber,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages());
        return new ApiPage(data, pagination);
    }

    private CourseworkResponse toResponse(Coursework coursework) {
        long attachmentCount = attachmentService.countFor(coursework.getId());
        if (!currentRole().isStudentLike()) {
            return CourseworkResponse.from(coursework, attachmentCount, null);
        }
        CourseworkMyProgress myProgress =
                progressService.resolveMyProgress(coursework.getId(), currentPrincipal().getId());
        return CourseworkResponse.from(coursework, attachmentCount, myProgress);
    }

    private void validatePublishable(Coursework coursework) {
        requireText(coursework.getTitle(), "title");
        requireText(coursework.getDescription(), "description");
        if (coursework.getType() == null) {
            throw invalidData("type is required");
        }
        if (coursework.getDueAt() == null) {
            throw invalidData("dueAt is required");
        }
        validateDeadlineOrdering(coursework.getIssuedAt(), coursework.getDueAt());
        normalizeWeight(coursework.getWeight());
        normalizeSourceUrl(coursework.getSourceType(), coursework.getSourceUrl());
        if (coursework.getCourseUnit() == null) {
            throw invalidData("courseUnit is required");
        }
    }

    private void enforceCourseworkReadAccess(Coursework coursework) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        membershipAccessService.requireCanAccessClass(
                currentPrincipal().getId(),
                coursework.getCourseUnit().getAcademicClass().getId());
    }

    private void enforceCourseworkManagementAccess(Coursework coursework) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        if (role == UserRole.CLASS_REP) {
            membershipAccessService.requireClassRepForClass(
                    currentPrincipal().getId(),
                    coursework.getCourseUnit().getAcademicClass().getId());
            return;
        }
        throw new ApplicationException(ErrorCodes.FORBIDDEN, "Forbidden", HttpStatus.FORBIDDEN);
    }

    private void enforceCourseUnitManagementAccess(CourseUnit courseUnit) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        if (role == UserRole.CLASS_REP) {
            membershipAccessService.requireClassRepForClass(
                    currentPrincipal().getId(), courseUnit.getAcademicClass().getId());
            return;
        }
        throw new ApplicationException(ErrorCodes.FORBIDDEN, "Forbidden", HttpStatus.FORBIDDEN);
    }

    private CourseUnit requireCourseUnit(UUID courseUnitId) {
        return courseUnitRepository
                .findById(courseUnitId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.COURSE_UNIT_NOT_FOUND,
                        "Course unit not found",
                        HttpStatus.NOT_FOUND));
    }

    private Coursework requireCoursework(UUID id) {
        return courseworkRepository.findDetailedById(id).orElseThrow(this::notFound);
    }

    private User requireCurrentUser() {
        ClassHubUserDetails principal = currentPrincipal();
        return userRepository
                .findById(principal.getId())
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.UNAUTHENTICATED,
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED));
    }

    private static Instant requireDueAt(Instant dueAt, boolean mustBeFuture) {
        if (dueAt == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DEADLINE,
                    "dueAt is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (mustBeFuture && !dueAt.isAfter(Instant.now())) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DEADLINE,
                    "dueAt must be in the future",
                    HttpStatus.BAD_REQUEST);
        }
        return dueAt;
    }

    private static void validateDeadlineOrdering(Instant issuedAt, Instant dueAt) {
        if (issuedAt != null && !dueAt.isAfter(issuedAt)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DEADLINE,
                    "dueAt must be after issuedAt",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static BigDecimal normalizeWeight(BigDecimal weight) {
        if (weight == null) {
            return null;
        }
        if (weight.compareTo(MIN_WEIGHT) < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_DATA,
                    "weight must be greater than 0 and at most 100",
                    HttpStatus.BAD_REQUEST);
        }
        return weight;
    }

    private static String normalizeSourceUrl(CourseworkSourceType sourceType, String sourceUrl) {
        String trimmed = normalizeOptionalText(sourceUrl);
        if (SOURCES_REQUIRING_URL.contains(sourceType)) {
            if (trimmed == null) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_COURSEWORK_SOURCE,
                        "sourceUrl is required for sourceType " + sourceType,
                        HttpStatus.BAD_REQUEST);
            }
            return requireHttpUrl(trimmed);
        }
        if (trimmed == null) {
            return null;
        }
        return requireHttpUrl(trimmed);
    }

    private static String requireHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("file:")) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSEWORK_SOURCE,
                    "sourceUrl scheme is not allowed",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw invalidSourceUrl();
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw invalidSourceUrl();
        }
    }

    private static ApplicationException invalidSourceUrl() {
        return new ApplicationException(
                ErrorCodes.INVALID_COURSEWORK_SOURCE,
                "sourceUrl must be a valid http or https URL",
                HttpStatus.BAD_REQUEST);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalidData(field + " is required");
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw invalidData(field + " is required");
        }
        return trimmed;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApplicationException invalidData(String message) {
        return new ApplicationException(ErrorCodes.INVALID_COURSEWORK_DATA, message, HttpStatus.BAD_REQUEST);
    }

    private static ApplicationException invalidState(String message) {
        return new ApplicationException(ErrorCodes.INVALID_COURSEWORK_STATE, message, HttpStatus.CONFLICT);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                ErrorCodes.COURSEWORK_NOT_FOUND,
                "Coursework not found",
                HttpStatus.NOT_FOUND);
    }

    private static UserRole currentRole() {
        return currentPrincipal().getRole();
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

    public record ApiPage(List<CourseworkResponse> data, Pagination pagination) {
    }
}
