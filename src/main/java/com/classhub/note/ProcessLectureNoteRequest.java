package com.classhub.note;

import com.classhub.ai.AiNoteOperation;
import jakarta.validation.constraints.NotNull;

public record ProcessLectureNoteRequest(@NotNull AiNoteOperation operation) {
}
