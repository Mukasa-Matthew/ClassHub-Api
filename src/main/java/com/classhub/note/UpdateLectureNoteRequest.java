package com.classhub.note;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record UpdateLectureNoteRequest(
        UUID courseUnitId,
        @Size(max = 300) String title,
        @Size(max = 50000) String rawContent,
        Instant lectureStartedAt) {
}
