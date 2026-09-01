package com.classhub.announcement;

import jakarta.validation.constraints.Size;

public record UpdateAnnouncementRequest(
        @Size(max = 300) String title, @Size(max = 10000) String content) {
}
