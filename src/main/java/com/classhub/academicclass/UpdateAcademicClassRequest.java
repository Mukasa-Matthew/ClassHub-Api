package com.classhub.academicclass;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateAcademicClassRequest(
        @Size(max = 200) String name,
        @Size(max = 200) String programmeName,
        @Size(max = 50) String programmeCode,
        @Min(1) @Max(10) Integer studyYear,
        @Min(1) @Max(4) Integer semester,
        @Min(1900) @Max(2100) Integer academicYear,
        AcademicClassStatus status) {
}
