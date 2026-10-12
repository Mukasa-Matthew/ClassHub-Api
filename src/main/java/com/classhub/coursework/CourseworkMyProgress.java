package com.classhub.coursework;

import java.time.Instant;

/** Student-only progress summary embedded in coursework list/detail responses. */
public record CourseworkMyProgress(CourseworkProgressStatus status, Instant completedAt) {

    public static CourseworkMyProgress notStarted() {
        return new CourseworkMyProgress(CourseworkProgressStatus.NOT_STARTED, null);
    }

    public static CourseworkMyProgress from(CourseworkProgress progress) {
        return new CourseworkMyProgress(progress.getProgressStatus(), progress.getCompletedAt());
    }

    public static CourseworkMyProgress from(CourseworkProgressResponse response) {
        return new CourseworkMyProgress(response.status(), response.completedAt());
    }
}
