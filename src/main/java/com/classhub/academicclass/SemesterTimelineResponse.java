package com.classhub.academicclass;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SemesterTimelineResponse(
        String className,
        String programmeName,
        String semesterName,
        LocalDate startDate,
        LocalDate endDate,
        long totalDays,
        long elapsedDays,
        long remainingDays,
        BigDecimal progressPercentage,
        SemesterTimelineState state) {
}
