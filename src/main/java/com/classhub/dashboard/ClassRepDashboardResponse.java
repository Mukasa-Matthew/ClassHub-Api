package com.classhub.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClassRepDashboardResponse(
        long activeCourseUnits,
        long draftCoursework,
        long publishedCoursework,
        long overduePublishedCoursework,
        long draftAnnouncements,
        long publishedAnnouncements,
        long activeStudentCount,
        List<UpcomingDeadlineItem> upcomingDeadlines,
        List<RecentAnnouncementItem> recentAnnouncements) {

    public record UpcomingDeadlineItem(
            UUID courseworkId, String title, String courseUnitName, Instant dueAt) {
    }

    public record RecentAnnouncementItem(UUID id, String title, Instant publishedAt) {
    }
}
