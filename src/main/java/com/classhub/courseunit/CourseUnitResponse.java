package com.classhub.courseunit;

import java.time.Instant;
import java.util.UUID;

public record CourseUnitResponse(
        UUID id,
        String code,
        String name,
        String lecturerName,
        String description,
        boolean active,
        boolean hasCoverImage,
        String coverImageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static CourseUnitResponse from(CourseUnit unit) {
        boolean hasCover = unit.hasCoverImage();
        String coverUrl = hasCover ? coverImagePath(unit.getId()) : null;
        return new CourseUnitResponse(
                unit.getId(),
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

    static String coverImagePath(UUID courseUnitId) {
        return "/api/v1/course-units/" + courseUnitId + "/cover-image";
    }
}
