package com.classhub.academicclass;

import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SemesterTimelineService {

    private final AcademicClassRepository academicClassRepository;
    private final ClassMembershipAccessService accessService;
    private final AuditService auditService;
    private final Clock clock;

    public SemesterTimelineService(
            AcademicClassRepository academicClassRepository,
            ClassMembershipAccessService accessService,
            AuditService auditService,
            Clock clock) {
        this.academicClassRepository = academicClassRepository;
        this.accessService = accessService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SemesterTimelineResponse getForActiveMember(UUID userId) {
        ClassMembership membership = accessService.requireActiveMembership(userId);
        return toResponse(membership.getAcademicClass(), today());
    }

    @Transactional
    public SemesterTimelineResponse updateForClassRep(UUID classRepUserId, UpdateSemesterTimelineRequest request) {
        ClassMembership repMembership = accessService.requireClassRepMembership(classRepUserId);
        AcademicClass academicClass = repMembership.getAcademicClass();

        String semesterName = normalizeSemesterName(request.semesterName());
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        validateSemesterDates(startDate, endDate);

        academicClass.updateSemesterTimeline(semesterName, startDate, endDate);
        AcademicClass saved = academicClassRepository.saveAndFlush(academicClass);

        auditService.record(
                AuditAction.CLASS_SEMESTER_TIMELINE_UPDATED,
                AuditEntityTypes.ACADEMIC_CLASS,
                saved.getId(),
                buildAuditSummary(saved));

        return toResponse(saved, today());
    }

    static String normalizeSemesterName(String semesterName) {
        if (semesterName == null) {
            return null;
        }
        String trimmed = semesterName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static void validateSemesterDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return;
        }
        if (startDate == null || endDate == null) {
            throw invalidSemesterTimeline("Both startDate and endDate are required when configuring a semester");
        }
        if (!startDate.isBefore(endDate)) {
            throw invalidSemesterTimeline("startDate must be before endDate");
        }
    }

    private SemesterTimelineResponse toResponse(AcademicClass academicClass, LocalDate today) {
        if (!academicClass.isSemesterTimelineConfigured()) {
            return new SemesterTimelineResponse(
                    academicClass.getName(),
                    academicClass.getProgrammeName(),
                    academicClass.getSemesterName(),
                    null,
                    null,
                    0,
                    0,
                    0,
                    java.math.BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP),
                    SemesterTimelineState.NOT_CONFIGURED);
        }

        SemesterTimelineCalculator.Metrics metrics = SemesterTimelineCalculator.calculate(
                academicClass.getSemesterStartDate(),
                academicClass.getSemesterEndDate(),
                today);

        return new SemesterTimelineResponse(
                academicClass.getName(),
                academicClass.getProgrammeName(),
                academicClass.getSemesterName(),
                academicClass.getSemesterStartDate(),
                academicClass.getSemesterEndDate(),
                metrics.totalDays(),
                metrics.elapsedDays(),
                metrics.remainingDays(),
                metrics.progressPercentage(),
                metrics.state());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private static String buildAuditSummary(AcademicClass academicClass) {
        if (!academicClass.isSemesterTimelineConfigured()) {
            return "Cleared semester timeline for class " + academicClass.getName();
        }
        return "Updated semester timeline for class "
                + academicClass.getName()
                + ": "
                + (academicClass.getSemesterName() == null ? "Unnamed semester" : academicClass.getSemesterName())
                + " ("
                + academicClass.getSemesterStartDate()
                + " to "
                + academicClass.getSemesterEndDate()
                + ")";
    }

    private static ApplicationException invalidSemesterTimeline(String message) {
        return new ApplicationException(ErrorCodes.INVALID_CLASS_DATA, message, HttpStatus.BAD_REQUEST);
    }
}
