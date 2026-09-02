package com.classhub.courseunit;

import java.time.Instant;
import java.util.UUID;

public record SuperAdminCourseUnitResponse(
        UUID id,
        String internalCode,
        String code,
        String name,
        String lecturerName,
        String description,
        boolean active,
        boolean hasCoverImage,
        String coverImageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static SuperAdminCourseUnitResponse from(CourseUnit unit) {
        boolean hasCover = unit.hasCoverImage();
        String coverUrl = hasCover ? CourseUnitResponse.coverImagePath(unit.getId()) : null;
        return new SuperAdminCourseUnitResponse(
                unit.getId(),
                unit.getInternalCode(),
                unit.getCode(),
                unit.getName(),
                unit.getLecturerName(),
                unit.getDescription(),
                unit.isActive(),
                hasCover,
                coverUrl,
                unit.getCreatedAt(),
                unit.getUpdatedAt());
    }
}
