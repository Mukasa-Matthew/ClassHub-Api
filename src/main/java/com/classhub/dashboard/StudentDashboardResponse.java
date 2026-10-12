package com.classhub.dashboard;

import com.classhub.coursework.CourseworkMyProgress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentDashboardResponse(
        long totalActiveCourseUnits,
        long pendingCourseworkCount,
        long inProgressCourseworkCount,
        long completedCourseworkCount,
        long overdueCourseworkCount,
        long dueSoonCourseworkCount,
        long unreadNotificationCount,
        List<RecentAnnouncementItem> recentAnnouncements,
        List<UpcomingCourseworkItem> upcomingCoursework) {

    public record RecentAnnouncementItem(UUID id, String title, Instant publishedAt) {
    }

    public record UpcomingCourseworkItem(
            UUID courseworkId,
            String title,
            String courseUnitName,
            Instant dueAt,
            CourseworkMyProgress myProgress,
            long attachmentCount) {
    }
}
