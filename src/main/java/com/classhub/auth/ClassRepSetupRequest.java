package com.classhub.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClassRepSetupRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 200) String programmeName,
        @Min(1) @Max(10) int studyYear,
        @Min(1) @Max(4) int semester,
        @Min(1900) @Max(2100) int academicYear) {
}
