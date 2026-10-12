package com.classhub.announcement;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String content,
        AnnouncementStatus status,
        UUID createdByUserId,
        String createdByName,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AnnouncementResponse from(Announcement announcement) {
        var creator = announcement.getCreatedBy();
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getStatus(),
                creator.getId(),
                creator.getFirstName() + " " + creator.getLastName(),
                announcement.getPublishedAt(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt());
    }
}
