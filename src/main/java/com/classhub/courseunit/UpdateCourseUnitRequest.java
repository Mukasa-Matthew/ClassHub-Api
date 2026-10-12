package com.classhub.courseunit;

import jakarta.validation.constraints.Size;

public record UpdateCourseUnitRequest(
        @Size(max = 50) String code,
        @Size(max = 200) String name,
        @Size(max = 200) String lecturerName,
        @Size(max = 5000) String description) {
}
