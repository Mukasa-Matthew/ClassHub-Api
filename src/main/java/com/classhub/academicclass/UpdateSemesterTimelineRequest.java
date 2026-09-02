package com.classhub.academicclass;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateSemesterTimelineRequest(
        @Size(max = 200) String semesterName, LocalDate startDate, LocalDate endDate) {
}
