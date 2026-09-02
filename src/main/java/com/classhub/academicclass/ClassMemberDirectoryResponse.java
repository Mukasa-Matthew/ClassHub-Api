package com.classhub.academicclass;

import java.time.Instant;
import java.util.UUID;

public record ClassMemberDirectoryResponse(
        UUID membershipId,
        UUID userId,
        String fullName,
        String registrationNumber,
        String email,
        String phoneNumber,
        MembershipRole membershipRole,
        MembershipStatus membershipStatus,
        Instant requestedAt,
        Instant approvedAt) {
}
