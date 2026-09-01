package com.classhub.note;

import com.classhub.ai.AiNoteOperation;
import java.time.Instant;
import java.util.UUID;

public record LectureNoteAiOutputResponse(
        UUID id,
        AiNoteOperation operation,
        String content,
        String modelProvider,
        String modelName,
        Instant createdAt) {

    public static LectureNoteAiOutputResponse from(LectureNoteAiOutput output) {
        return new LectureNoteAiOutputResponse(
                output.getId(),
                output.getOperation(),
                output.getContent(),
                output.getModelProvider(),
                output.getModelName(),
                output.getCreatedAt());
    }
}
