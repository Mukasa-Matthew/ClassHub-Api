package com.classhub.coursework;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateCourseworkRequest(
        UUID courseUnitId,
        @Size(max = 300) String title,
        @Size(max = 10000) String description,
        @Size(max = 10000) String instructions,
        CourseworkType type,
        Instant issuedAt,
        Instant dueAt,
        @DecimalMin(value = "0.01", inclusive = true) @DecimalMax(value = "100", inclusive = true)
                BigDecimal weight,
        CourseworkSourceType sourceType,
        @Size(max = 2048) String sourceUrl,
        @Size(max = 200) String sourceLabel,
        Boolean notifyStudentsOfUpdate) {
}
