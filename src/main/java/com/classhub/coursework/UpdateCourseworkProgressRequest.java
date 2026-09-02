package com.classhub.coursework;

import jakarta.validation.constraints.NotNull;

public record UpdateCourseworkProgressRequest(@NotNull CourseworkProgressStatus status) {
}
