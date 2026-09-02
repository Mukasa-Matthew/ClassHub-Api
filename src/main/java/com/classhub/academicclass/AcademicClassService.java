package com.classhub.academicclass;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicClassService {

    private final AcademicClassRepository academicClassRepository;
    private final ClassMembershipRepository membershipRepository;
    private final JoinCodeGenerator joinCodeGenerator;
    private final UserService userService;
    private final AuditService auditService;

    public AcademicClassService(
            AcademicClassRepository academicClassRepository,
            ClassMembershipRepository membershipRepository,
            JoinCodeGenerator joinCodeGenerator,
            UserService userService,
            AuditService auditService) {
        this.academicClassRepository = academicClassRepository;
        this.membershipRepository = membershipRepository;
        this.joinCodeGenerator = joinCodeGenerator;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional
    public AcademicClassResponse create(CreateAcademicClassRequest request) {
        String name = requireText(request.name(), "name");
        AcademicClass academicClass = new AcademicClass(
                name,
                requireText(request.programmeName(), "programmeName"),
                normalizeOptional(request.programmeCode()),
                request.studyYear(),
                request.semester(),
                request.academicYear(),
                joinCodeGenerator.generateUnique(academicClassRepository),
                AcademicClassStatus.ACTIVE);
        AcademicClass saved = academicClassRepository.saveAndFlush(academicClass);
        auditService.record(
                AuditAction.CLASS_CREATED,
                AuditEntityTypes.ACADEMIC_CLASS,
                saved.getId(),
                "Created class " + saved.getName());
        return AcademicClassResponse.from(saved, true);
    }

    @Transactional(readOnly = true)
    public List<AcademicClassResponse> list() {
        return academicClassRepository.findAllByOrderByNameAsc().stream()
                .map(c -> AcademicClassResponse.from(c, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public AcademicClassResponse getById(UUID id) {
        AcademicClass academicClass = requireClass(id);
        return AcademicClassResponse.from(academicClass, true);
    }

    @Transactional
    public AcademicClassResponse update(UUID id, UpdateAcademicClassRequest request) {
        AcademicClass academicClass = requireClass(id);
        String name = request.name() == null ? academicClass.getName() : requireText(request.name(), "name");
        String programmeName = request.programmeName() == null
                ? academicClass.getProgrammeName()
                : requireText(request.programmeName(), "programmeName");
        String programmeCode = request.programmeCode() == null
                ? academicClass.getProgrammeCode()
                : normalizeOptional(request.programmeCode());
        AcademicClassStatus status = request.status() == null ? academicClass.getStatus() : request.status();
        int studyYear = request.studyYear() == null ? academicClass.getStudyYear() : request.studyYear();
        int semester = request.semester() == null ? academicClass.getSemester() : request.semester();
        int academicYear = request.academicYear() == null ? academicClass.getAcademicYear() : request.academicYear();
        academicClass.updateDetails(
                name, programmeName, programmeCode, studyYear, semester, academicYear, status);
        AcademicClass saved = academicClassRepository.saveAndFlush(academicClass);
        auditService.record(
                AuditAction.CLASS_UPDATED,
                AuditEntityTypes.ACADEMIC_CLASS,
                saved.getId(),
                "Updated class " + saved.getName());
        return AcademicClassResponse.from(saved, true);
    }

    @Transactional
    public void assignClassRepresentative(UUID classId, UUID userId) {
        AcademicClass academicClass = requireClass(classId);
        User user = userService.getById(userId);
        if (user.getRole() != UserRole.CLASS_REP) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ROLE_CHANGE,
                    "User must have CLASS_REP role",
                    HttpStatus.BAD_REQUEST);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_STATUS_CHANGE,
                    "User must be active",
                    HttpStatus.BAD_REQUEST);
        }

        ClassMembership membership = membershipRepository
                .findByAcademicClassIdAndUserId(classId, userId)
                .orElseGet(() -> new ClassMembership(
                        academicClass,
                        user,
                        MembershipRole.CLASS_REP,
                        MembershipStatus.PENDING,
                        Instant.now()));

        membership.assignAsClassRep(user, Instant.now());
        try {
            membershipRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException ex) {
            membership = membershipRepository
                    .findByAcademicClassIdAndUserId(classId, userId)
                    .orElseThrow();
            membership.assignAsClassRep(user, Instant.now());
            membershipRepository.saveAndFlush(membership);
        }

        auditService.record(
                AuditAction.CLASS_REP_ASSIGNED,
                AuditEntityTypes.ACADEMIC_CLASS,
                academicClass.getId(),
                "Assigned class representative " + user.getEmail());
    }

    @Transactional(readOnly = true)
    public AcademicClass requireActiveClassByJoinCode(String joinCode) {
        String normalized = JoinCodeGenerator.normalize(joinCode);
        if (normalized.isEmpty()) {
            throw invalidJoinCode();
        }
        AcademicClass academicClass = academicClassRepository
                .findByJoinCodeIgnoreCase(normalized)
                .orElseThrow(this::invalidJoinCode);
        if (academicClass.getStatus() != AcademicClassStatus.ACTIVE) {
            throw invalidJoinCode();
        }
        return academicClass;
    }

    public AcademicClass requireClass(UUID id) {
        return academicClassRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.CLASS_NOT_FOUND,
                        "Class not found",
                        HttpStatus.NOT_FOUND));
    }

    private ApplicationException invalidJoinCode() {
        return new ApplicationException(
                ErrorCodes.INVALID_JOIN_CODE,
                "Join code is invalid",
                HttpStatus.BAD_REQUEST);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_CLASS_DATA,
                    field + " is required",
                    HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
