package com.classhub.academicclass;

import jakarta.validation.constraints.Size;

public record UpdateAcademicClassRequest(
        @Size(max = 200) String name,
        @Size(max = 200) String programmeName,
        @Size(max = 50) String programmeCode,
        AcademicClassStatus status) {
}
