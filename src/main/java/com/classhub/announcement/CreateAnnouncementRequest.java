package com.classhub.announcement;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnnouncementRequest(
        UUID classId,
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 10000) String content) {
}
