package com.classhub.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateLectureNoteRequest(
        @NotNull UUID courseUnitId,
        @Size(max = 300) String title,
        @NotBlank @Size(max = 50000) String rawContent,
        Instant lectureStartedAt) {
}
