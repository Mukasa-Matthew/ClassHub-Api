package com.classhub.courseunit;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseUnitRequest(
        UUID classId,
        @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String lecturerName,
        @Size(max = 5000) String description) {
}
