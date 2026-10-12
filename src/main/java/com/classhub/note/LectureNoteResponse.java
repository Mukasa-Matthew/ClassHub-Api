package com.classhub.note;

import java.time.Instant;
import java.util.UUID;

public record LectureNoteResponse(
        UUID id,
        UUID courseUnitId,
        String courseUnitName,
        String title,
        String rawContent,
        LectureNoteStatus status,
        Instant lectureStartedAt,
        Instant lectureEndedAt,
        long aiOutputCount,
        Instant createdAt,
        Instant updatedAt) {

    public static LectureNoteResponse from(LectureNote note, long aiOutputCount) {
        var unit = note.getCourseUnit();
        return new LectureNoteResponse(
                note.getId(),
                unit.getId(),
                unit.getName(),
                note.getTitle(),
                note.getRawContent(),
                note.getStatus(),
                note.getLectureStartedAt(),
                note.getLectureEndedAt(),
                aiOutputCount,
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
