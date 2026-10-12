package com.classhub.coursework;

import java.time.Instant;
import java.util.UUID;

public record CourseworkAttachmentResponse(
        UUID id,
        UUID courseworkId,
        String originalFileName,
        String contentType,
        long fileSize,
        Instant createdAt) {

    public static CourseworkAttachmentResponse from(CourseworkAttachment attachment) {
        return new CourseworkAttachmentResponse(
                attachment.getId(),
                attachment.getCoursework().getId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getCreatedAt());
    }
}
