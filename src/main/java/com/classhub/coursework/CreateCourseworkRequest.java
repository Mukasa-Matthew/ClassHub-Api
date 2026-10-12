package com.classhub.coursework;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateCourseworkRequest(
        @NotNull UUID courseUnitId,
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 10000) String description,
        @Size(max = 10000) String instructions,
        @NotNull CourseworkType type,
        Instant issuedAt,
        @NotNull Instant dueAt,
        @DecimalMin(value = "0.01", inclusive = true) @DecimalMax(value = "100", inclusive = true)
                BigDecimal weight,
        @NotNull CourseworkSourceType sourceType,
        @Size(max = 2048) String sourceUrl,
        @Size(max = 200) String sourceLabel) {
}
