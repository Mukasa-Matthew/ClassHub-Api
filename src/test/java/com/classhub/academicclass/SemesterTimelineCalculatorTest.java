package com.classhub.academicclass;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SemesterTimelineCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 25);
    private static final LocalDate END = LocalDate.of(2026, 12, 12);

    @Test
    void reportsUpcomingBeforeStartWithZeroProgress() {
        SemesterTimelineCalculator.Metrics metrics =
                SemesterTimelineCalculator.calculate(START, END, LocalDate.of(2026, 8, 24));

        assertThat(metrics.state()).isEqualTo(SemesterTimelineState.UPCOMING);
        assertThat(metrics.totalDays()).isEqualTo(110);
        assertThat(metrics.elapsedDays()).isZero();
        assertThat(metrics.remainingDays()).isEqualTo(110);
        assertThat(metrics.progressPercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void reportsInProgressOnFirstDay() {
        SemesterTimelineCalculator.Metrics metrics =
                SemesterTimelineCalculator.calculate(START, END, START);

        assertThat(metrics.state()).isEqualTo(SemesterTimelineState.IN_PROGRESS);
        assertThat(metrics.elapsedDays()).isEqualTo(1);
        assertThat(metrics.remainingDays()).isEqualTo(109);
        assertThat(metrics.progressPercentage()).isEqualByComparingTo("0.91");
    }

    @Test
    void reportsInProgressMidSemesterWithClampedValues() {
        SemesterTimelineCalculator.Metrics metrics =
                SemesterTimelineCalculator.calculate(START, END, LocalDate.of(2026, 9, 15));

        assertThat(metrics.state()).isEqualTo(SemesterTimelineState.IN_PROGRESS);
        assertThat(metrics.elapsedDays()).isEqualTo(22);
        assertThat(metrics.remainingDays()).isEqualTo(88);
        assertThat(metrics.progressPercentage()).isEqualByComparingTo("20.00");
    }

    @Test
    void reportsInProgressOnLastDayWithFullProgress() {
        SemesterTimelineCalculator.Metrics metrics =
                SemesterTimelineCalculator.calculate(START, END, END);

        assertThat(metrics.state()).isEqualTo(SemesterTimelineState.IN_PROGRESS);
        assertThat(metrics.elapsedDays()).isEqualTo(110);
        assertThat(metrics.remainingDays()).isZero();
        assertThat(metrics.progressPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void reportsCompletedAfterEndDate() {
        SemesterTimelineCalculator.Metrics metrics =
                SemesterTimelineCalculator.calculate(START, END, LocalDate.of(2026, 12, 13));

        assertThat(metrics.state()).isEqualTo(SemesterTimelineState.COMPLETED);
        assertThat(metrics.elapsedDays()).isEqualTo(110);
        assertThat(metrics.remainingDays()).isZero();
        assertThat(metrics.progressPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void neverReturnsNegativeRemainingOrProgressAboveOneHundred() {
        SemesterTimelineCalculator.Metrics metrics =
                SemesterTimelineCalculator.calculate(START, END, LocalDate.of(2027, 1, 1));

        assertThat(metrics.remainingDays()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.elapsedDays()).isLessThanOrEqualTo(metrics.totalDays());
        assertThat(metrics.progressPercentage()).isLessThanOrEqualTo(BigDecimal.valueOf(100).setScale(2));
        assertThat(metrics.progressPercentage()).isGreaterThanOrEqualTo(BigDecimal.ZERO.setScale(2));
    }
}
