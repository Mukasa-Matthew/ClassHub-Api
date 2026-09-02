package com.classhub.courseunit;

import jakarta.validation.constraints.NotNull;

public record UpdateCourseUnitStatusRequest(@NotNull Boolean active) {
}
