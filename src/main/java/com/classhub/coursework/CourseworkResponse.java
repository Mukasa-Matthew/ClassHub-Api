package com.classhub.coursework;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseworkResponse(
        UUID id,
        UUID courseUnitId,
        String courseUnitCode,
        String courseUnitName,
        String title,
        String description,
        String instructions,
        CourseworkType type,
        Instant issuedAt,
        Instant dueAt,
        BigDecimal weight,
        CourseworkSourceType sourceType,
        String sourceUrl,
        String sourceLabel,
        CourseworkStatus status,
        UUID createdByUserId,
        String createdByName,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long attachmentCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) CourseworkMyProgress myProgress) {

    public static CourseworkResponse from(Coursework coursework) {
        return from(coursework, 0L, null);
    }

    public static CourseworkResponse from(Coursework coursework, CourseworkMyProgress myProgress) {
        return from(coursework, 0L, myProgress);
    }

    public static CourseworkResponse from(
            Coursework coursework, long attachmentCount, CourseworkMyProgress myProgress) {
        var unit = coursework.getCourseUnit();
        var creator = coursework.getCreatedBy();
        return new CourseworkResponse(
                coursework.getId(),
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                coursework.getTitle(),
                coursework.getDescription(),
                coursework.getInstructions(),
                coursework.getType(),
                coursework.getIssuedAt(),
                coursework.getDueAt(),
                coursework.getWeight(),
                coursework.getSourceType(),
                coursework.getSourceUrl(),
                coursework.getSourceLabel(),
                coursework.getStatus(),
                creator.getId(),
                creator.getFirstName() + " " + creator.getLastName(),
                coursework.getPublishedAt(),
                coursework.getCreatedAt(),
                coursework.getUpdatedAt(),
                attachmentCount,
                myProgress);
    }
}
