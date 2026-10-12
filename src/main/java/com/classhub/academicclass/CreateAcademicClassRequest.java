package com.classhub.academicclass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAcademicClassRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String programmeName,
        @Size(max = 50) String programmeCode,
        @NotNull @Min(1) @Max(10) Integer studyYear,
        @NotNull @Min(1) @Max(4) Integer semester,
        @NotNull @Min(1900) @Max(2100) Integer academicYear) {
}
