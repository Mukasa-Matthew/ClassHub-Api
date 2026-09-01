package com.classhub.academicclass;

import java.util.List;
import java.util.UUID;

public record ClassListResponse(AcademicClassSummary classInfo, List<ClassListMemberResponse> members) {

    public record AcademicClassSummary(
            UUID id,
            String name,
            String programmeName,
            String programmeCode) {
    }

    public record ClassListMemberResponse(
            String fullName,
            String registrationNumber,
            String email,
            String phoneNumber,
            MembershipRole membershipRole) {
    }
}
