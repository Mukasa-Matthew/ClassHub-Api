package com.classhub.academicclass;

import com.classhub.user.User;
import java.time.Instant;
import java.util.UUID;

public record ClassMembershipResponse(
        UUID membershipId,
        UUID classId,
        String className,
        String programmeName,
        String programmeCode,
        int studyYear,
        int semester,
        int academicYear,
        MembershipRole membershipRole,
        MembershipStatus status,
        Instant requestedAt,
        Instant approvedAt) {

    static ClassMembershipResponse from(ClassMembership membership) {
        AcademicClass academicClass = membership.getAcademicClass();
        return new ClassMembershipResponse(
                membership.getId(),
                academicClass.getId(),
                academicClass.getName(),
                academicClass.getProgrammeName(),
                academicClass.getProgrammeCode(),
                academicClass.getStudyYear(),
                academicClass.getSemester(),
                academicClass.getAcademicYear(),
                membership.getMembershipRole(),
                membership.getStatus(),
                membership.getRequestedAt(),
                membership.getApprovedAt());
    }
}
