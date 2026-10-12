package com.classhub.dashboard;

import com.classhub.audit.AuditLogResponse;
import java.util.List;

public record AdminDashboardResponse(
        long totalUsers,
        long activeStudents,
        long activeClassReps,
        long suspendedUsers,
        long disabledUsers,
        long activeCourseUnits,
        long totalPublishedCoursework,
        long totalPublishedAnnouncements,
        long totalAttachments,
        List<AuditLogResponse> recentAdministrativeActions) {
}
