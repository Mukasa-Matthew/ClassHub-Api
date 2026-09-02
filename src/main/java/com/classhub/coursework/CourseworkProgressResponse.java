package com.classhub.coursework;

import java.time.Instant;
import java.util.UUID;

public record CourseworkProgressResponse(
        UUID courseworkId, CourseworkProgressStatus status, Instant completedAt) {

    public static CourseworkProgressResponse notStarted(UUID courseworkId) {
        return new CourseworkProgressResponse(courseworkId, CourseworkProgressStatus.NOT_STARTED, null);
    }

    public static CourseworkProgressResponse from(CourseworkProgress progress) {
        return new CourseworkProgressResponse(
                progress.getCoursework().getId(),
                progress.getProgressStatus(),
                progress.getCompletedAt());
    }
}
