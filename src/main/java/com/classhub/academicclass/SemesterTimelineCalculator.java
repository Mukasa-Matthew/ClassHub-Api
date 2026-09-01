package com.classhub.academicclass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Date-only semester timeline calculations.
 *
 * <p>Convention: both start and end dates are inclusive. {@code totalDays} counts every calendar
 * day from start through end. On the first day of the semester, {@code elapsedDays} is 1 and
 * progress is {@code 100 / totalDays}. On the last day, {@code elapsedDays == totalDays},
 * {@code remainingDays == 0}, and progress is 100.
 */
final class SemesterTimelineCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);

    private SemesterTimelineCalculator() {
    }

    record Metrics(
            SemesterTimelineState state,
            long totalDays,
            long elapsedDays,
            long remainingDays,
            BigDecimal progressPercentage) {
    }

    static Metrics calculate(LocalDate startDate, LocalDate endDate, LocalDate today) {
        long totalDays = inclusiveDaysBetween(startDate, endDate);

        if (today.isBefore(startDate)) {
            return new Metrics(SemesterTimelineState.UPCOMING, totalDays, 0, totalDays, ZERO);
        }

        if (today.isAfter(endDate)) {
            return new Metrics(SemesterTimelineState.COMPLETED, totalDays, totalDays, 0, HUNDRED);
        }

        long elapsedDays = Math.min(inclusiveDaysBetween(startDate, today), totalDays);
        long remainingDays = Math.max(0, totalDays - elapsedDays);
        BigDecimal progress = BigDecimal.valueOf(elapsedDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        if (progress.compareTo(HUNDRED) > 0) {
            progress = HUNDRED;
        }
        if (progress.compareTo(ZERO) < 0) {
            progress = ZERO;
        }

        return new Metrics(
                SemesterTimelineState.IN_PROGRESS, totalDays, elapsedDays, remainingDays, progress);
    }

    private static long inclusiveDaysBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}
