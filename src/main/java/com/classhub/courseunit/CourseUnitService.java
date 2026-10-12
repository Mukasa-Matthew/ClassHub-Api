package com.classhub.courseunit;

import com.classhub.academicclass.AcademicClass;
import com.classhub.academicclass.AcademicClassService;
import com.classhub.academicclass.ClassMembershipAccessService;
import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseUnitService {

    private final CourseUnitRepository courseUnitRepository;
    private final CourseUnitInternalCodeGenerator internalCodeGenerator;
    private final AuditService auditService;
    private final ClassMembershipAccessService membershipAccessService;
    private final AcademicClassService academicClassService;

    public CourseUnitService(
            CourseUnitRepository courseUnitRepository,
            CourseUnitInternalCodeGenerator internalCodeGenerator,
            AuditService auditService,
            ClassMembershipAccessService membershipAccessService,
            AcademicClassService academicClassService) {
        this.courseUnitRepository = courseUnitRepository;
        this.internalCodeGenerator = internalCodeGenerator;
        this.auditService = auditService;
        this.membershipAccessService = membershipAccessService;
        this.academicClassService = academicClassService;
    }

    @Transactional
    public Object create(CreateCourseUnitRequest request) {
        UserRole role = currentRole();
        AcademicClass academicClass = resolveClassForCreate(role, request.classId());
        String displayName = requireDisplayName(request.name());
        String normalized = normalizeName(displayName);
        String code = normalizeOptional(request.code());
        String lecturerName = normalizeOptional(request.lecturerName());
        String description = normalizeOptional(request.description());

        if (courseUnitRepository.existsByAcademicClassIdAndNormalizedName(academicClass.getId(), normalized)) {
            throw alreadyExists();
        }

        String internalCode = internalCodeGenerator.nextCode();
        CourseUnit unit = new CourseUnit(
                academicClass, internalCode, code, displayName, normalized, lecturerName, description, true);
        try {
            CourseUnit saved = courseUnitRepository.saveAndFlush(unit);
            auditService.record(
                    AuditAction.COURSE_UNIT_CREATED,
                    AuditEntityTypes.COURSE_UNIT,
                    saved.getId(),
                    "Created course unit " + saved.getName());
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyExists();
        }
    }

    @Transactional(readOnly = true)
    public List<?> list(Boolean activeFilter) {
        UserRole role = currentRole();

        if (role == UserRole.CLASS_REP) {
            UUID classId = membershipAccessService.requireClassRepMembership(currentPrincipal().getId())
                    .getAcademicClass()
                    .getId();
            if (activeFilter == null) {
                return courseUnitRepository.findAllByAcademicClassIdOrderByNameAsc(classId).stream()
                        .map(this::toResponse)
                        .toList();
            }
            return courseUnitRepository.findByAcademicClassIdAndActiveOrderByNameAsc(classId, activeFilter).stream()
                    .map(this::toResponse)
                    .toList();
        }

        if (role == UserRole.STUDENT) {
            UUID classId = membershipAccessService.requireActiveClassId(currentPrincipal().getId());
            if (activeFilter != null && !activeFilter) {
                throw new ApplicationException(
                        ErrorCodes.FORBIDDEN,
                        "Students cannot list inactive course units",
                        HttpStatus.FORBIDDEN);
            }
            return courseUnitRepository.findByAcademicClassIdAndActiveOrderByNameAsc(classId, true).stream()
                    .map(this::toResponse)
                    .toList();
        }

        if (activeFilter == null) {
            return courseUnitRepository.findAll().stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .map(this::toResponse)
                    .toList();
        }
        return courseUnitRepository.findAll().stream()
                .filter(unit -> unit.isActive() == activeFilter)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Object getById(UUID id) {
        CourseUnit unit = courseUnitRepository.findById(id).orElseThrow(CourseUnitService::notFound);
        enforceClassAccess(unit);

        if (currentRole().isStudentLike() && !unit.isActive()) {
            throw notFound();
        }
        return toResponse(unit);
    }

    @Transactional
    public Object update(UUID id, UpdateCourseUnitRequest request) {
        CourseUnit unit = courseUnitRepository.findById(id).orElseThrow(CourseUnitService::notFound);
        enforceClassRepOrAdminAccess(unit);

        String displayName = request.name() == null ? unit.getName() : requireDisplayName(request.name());
        String normalized = normalizeName(displayName);
        String code = request.code() == null ? unit.getCode() : normalizeOptional(request.code());
        String lecturerName = request.lecturerName() == null
                ? unit.getLecturerName()
                : normalizeOptional(request.lecturerName());
        String description = request.description() == null
                ? unit.getDescription()
                : normalizeOptional(request.description());

        if (courseUnitRepository.existsByAcademicClassIdAndNormalizedNameAndIdNot(
                unit.getAcademicClass().getId(), normalized, id)) {
            throw alreadyExists();
        }

        unit.updateDetails(code, displayName, normalized, lecturerName, description);
        try {
            CourseUnit saved = courseUnitRepository.saveAndFlush(unit);
            auditService.record(
                    AuditAction.COURSE_UNIT_UPDATED,
                    AuditEntityTypes.COURSE_UNIT,
                    saved.getId(),
                    "Updated course unit " + saved.getName());
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyExists();
        }
    }

    @Transactional
    public Object updateStatus(UUID id, UpdateCourseUnitStatusRequest request) {
        CourseUnit unit = courseUnitRepository.findById(id).orElseThrow(CourseUnitService::notFound);
        unit.setActive(request.active());
        CourseUnit saved = courseUnitRepository.saveAndFlush(unit);
        auditService.record(
                AuditAction.COURSE_UNIT_STATUS_CHANGED,
                AuditEntityTypes.COURSE_UNIT,
                saved.getId(),
                "Set course unit " + saved.getName() + " active=" + saved.isActive());
        return toResponse(saved);
    }

    Object toResponse(CourseUnit unit) {
        if (currentRole() == UserRole.SUPER_ADMIN) {
            return SuperAdminCourseUnitResponse.from(unit);
        }
        return CourseUnitResponse.from(unit);
    }

    static String requireDisplayName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSE_UNIT_DATA,
                    "name is required",
                    HttpStatus.BAD_REQUEST);
        }
        String display = collapseWhitespace(name);
        if (display.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_COURSE_UNIT_DATA,
                    "name is required",
                    HttpStatus.BAD_REQUEST);
        }
        return display;
    }

    static String normalizeName(String displayName) {
        return displayName.toLowerCase();
    }

    static String collapseWhitespace(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = collapseWhitespace(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void enforceClassAccess(CourseUnit unit) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        membershipAccessService.requireCanAccessClass(
                currentPrincipal().getId(), unit.getAcademicClass().getId());
    }

    private void enforceClassRepOrAdminAccess(CourseUnit unit) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        if (role == UserRole.CLASS_REP) {
            membershipAccessService.requireClassRepForClass(
                    currentPrincipal().getId(), unit.getAcademicClass().getId());
            return;
        }
        throw new ApplicationException(ErrorCodes.FORBIDDEN, "Forbidden", HttpStatus.FORBIDDEN);
    }

    private AcademicClass resolveClassForCreate(UserRole role, UUID classId) {
        if (role == UserRole.CLASS_REP) {
            return membershipAccessService.requireClassRepMembership(currentPrincipal().getId())
                    .getAcademicClass();
        }
        if (role == UserRole.SUPER_ADMIN) {
            if (classId == null) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_COURSE_UNIT_DATA,
                        "classId is required",
                        HttpStatus.BAD_REQUEST);
            }
            return academicClassService.requireClass(classId);
        }
        throw new ApplicationException(ErrorCodes.FORBIDDEN, "Forbidden", HttpStatus.FORBIDDEN);
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

    private static UserRole currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal.getRole();
    }

    private static ApplicationException alreadyExists() {
        return new ApplicationException(
                ErrorCodes.COURSE_UNIT_ALREADY_EXISTS,
                "A course unit with this name already exists",
                HttpStatus.CONFLICT);
    }

    static ApplicationException notFound() {
        return new ApplicationException(
                ErrorCodes.COURSE_UNIT_NOT_FOUND,
                "Course unit not found",
                HttpStatus.NOT_FOUND);
    }
}
