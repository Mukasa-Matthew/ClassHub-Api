package com.classhub.dashboard;

import com.classhub.announcement.AnnouncementRepository;
import com.classhub.announcement.AnnouncementStatus;
import com.classhub.audit.AuditLogQueryService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.courseunit.CourseUnitRepository;
import com.classhub.coursework.Coursework;
import com.classhub.coursework.CourseworkAttachmentRepository;
import com.classhub.coursework.CourseworkMyProgress;
import com.classhub.coursework.CourseworkProgress;
import com.classhub.coursework.CourseworkProgressRepository;
import com.classhub.coursework.CourseworkProgressStatus;
import com.classhub.coursework.CourseworkRepository;
import com.classhub.coursework.CourseworkStatus;
import com.classhub.notification.NotificationRepository;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import com.classhub.user.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final int DUE_SOON_DAYS = 7;
    private static final int UPCOMING_LIMIT = 8;
    private static final int RECENT_ANNOUNCEMENTS_LIMIT = 5;
    private static final int RECENT_AUDIT_LIMIT = 8;

    private final CourseUnitRepository courseUnitRepository;
    private final CourseworkRepository courseworkRepository;
    private final CourseworkProgressRepository progressRepository;
    private final CourseworkAttachmentRepository attachmentRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuditLogQueryService auditLogQueryService;

    public DashboardService(
            CourseUnitRepository courseUnitRepository,
            CourseworkRepository courseworkRepository,
            CourseworkProgressRepository progressRepository,
            CourseworkAttachmentRepository attachmentRepository,
            AnnouncementRepository announcementRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            AuditLogQueryService auditLogQueryService) {
        this.courseUnitRepository = courseUnitRepository;
        this.courseworkRepository = courseworkRepository;
        this.progressRepository = progressRepository;
        this.attachmentRepository = attachmentRepository;
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.auditLogQueryService = auditLogQueryService;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse studentDashboard() {
        UUID studentId = requireCurrentUser().getId();
        Instant now = Instant.now();
        Instant dueSoonCutoff = now.plus(DUE_SOON_DAYS, ChronoUnit.DAYS);

        List<Coursework> published =
                courseworkRepository.findDetailedByStatusOrderByDueAtAsc(CourseworkStatus.PUBLISHED);
        List<UUID> courseworkIds = published.stream().map(Coursework::getId).toList();

        Map<UUID, CourseworkProgress> progressByCoursework = new HashMap<>();
        if (!courseworkIds.isEmpty()) {
            for (CourseworkProgress progress :
                    progressRepository.findByStudentIdAndCourseworkIdIn(studentId, courseworkIds)) {
                progressByCoursework.put(progress.getCoursework().getId(), progress);
            }
        }

        Map<UUID, Long> attachmentCounts = attachmentCounts(courseworkIds);

        long pending = 0;
        long inProgress = 0;
        long completed = 0;
        long overdue = 0;
        long dueSoon = 0;

        List<StudentDashboardResponse.UpcomingCourseworkItem> upcoming = new ArrayList<>();

        for (Coursework coursework : published) {
            CourseworkProgress progress = progressByCoursework.get(coursework.getId());
            CourseworkProgressStatus status = progress == null
                    ? CourseworkProgressStatus.NOT_STARTED
                    : progress.getProgressStatus();
            CourseworkMyProgress myProgress = progress == null
                    ? CourseworkMyProgress.notStarted()
                    : CourseworkMyProgress.from(progress);

            if (status == CourseworkProgressStatus.NOT_STARTED) {
                pending++;
            } else if (status == CourseworkProgressStatus.IN_PROGRESS) {
                inProgress++;
            } else if (status == CourseworkProgressStatus.COMPLETED) {
                completed++;
            }

            Instant dueAt = coursework.getDueAt();
            if (status != CourseworkProgressStatus.COMPLETED) {
                if (dueAt.isBefore(now)) {
                    overdue++;
                } else if (!dueAt.isAfter(dueSoonCutoff)) {
                    dueSoon++;
                }
            }

            if (upcoming.size() < UPCOMING_LIMIT) {
                upcoming.add(new StudentDashboardResponse.UpcomingCourseworkItem(
                        coursework.getId(),
                        coursework.getTitle(),
                        coursework.getCourseUnit().getName(),
                        dueAt,
                        myProgress,
                        attachmentCounts.getOrDefault(coursework.getId(), 0L)));
            }
        }

        List<StudentDashboardResponse.RecentAnnouncementItem> recentAnnouncements =
                announcementRepository
                        .findTop5ByStatusOrderByPublishedAtDesc(AnnouncementStatus.PUBLISHED)
                        .stream()
                        .limit(RECENT_ANNOUNCEMENTS_LIMIT)
                        .map(a -> new StudentDashboardResponse.RecentAnnouncementItem(
                                a.getId(), a.getTitle(), a.getPublishedAt()))
                        .toList();

        return new StudentDashboardResponse(
                courseUnitRepository.countByActive(true),
                pending,
                inProgress,
                completed,
                overdue,
                dueSoon,
                notificationRepository.countByRecipientIdAndReadFalse(studentId),
                recentAnnouncements,
                upcoming);
    }

    @Transactional(readOnly = true)
    public ClassRepDashboardResponse classRepDashboard() {
        Instant now = Instant.now();
        List<Coursework> published =
                courseworkRepository.findDetailedByStatusOrderByDueAtAsc(CourseworkStatus.PUBLISHED);

        long overduePublished = 0;
        List<ClassRepDashboardResponse.UpcomingDeadlineItem> upcoming = new ArrayList<>();
        for (Coursework coursework : published) {
            if (coursework.getDueAt().isBefore(now)) {
                overduePublished++;
            } else if (upcoming.size() < UPCOMING_LIMIT) {
                upcoming.add(new ClassRepDashboardResponse.UpcomingDeadlineItem(
                        coursework.getId(),
                        coursework.getTitle(),
                        coursework.getCourseUnit().getName(),
                        coursework.getDueAt()));
            }
        }

        List<ClassRepDashboardResponse.RecentAnnouncementItem> recentAnnouncements =
                announcementRepository
                        .findTop5ByStatusOrderByPublishedAtDesc(AnnouncementStatus.PUBLISHED)
                        .stream()
                        .limit(RECENT_ANNOUNCEMENTS_LIMIT)
                        .map(a -> new ClassRepDashboardResponse.RecentAnnouncementItem(
                                a.getId(), a.getTitle(), a.getPublishedAt()))
                        .toList();

        return new ClassRepDashboardResponse(
                courseUnitRepository.countByActive(true),
                courseworkRepository.countByStatus(CourseworkStatus.DRAFT),
                courseworkRepository.countByStatus(CourseworkStatus.PUBLISHED),
                overduePublished,
                announcementRepository.countByStatus(AnnouncementStatus.DRAFT),
                announcementRepository.countByStatus(AnnouncementStatus.PUBLISHED),
                userRepository.countByRoleAndStatus(UserRole.STUDENT, UserStatus.ACTIVE),
                upcoming,
                recentAnnouncements);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard() {
        return new AdminDashboardResponse(
                userRepository.count(),
                userRepository.countByRoleAndStatus(UserRole.STUDENT, UserStatus.ACTIVE),
                userRepository.countByRoleAndStatus(UserRole.CLASS_REP, UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countByStatus(UserStatus.DISABLED),
                courseUnitRepository.countByActive(true),
                courseworkRepository.countByStatus(CourseworkStatus.PUBLISHED),
                announcementRepository.countByStatus(AnnouncementStatus.PUBLISHED),
                attachmentRepository.count(),
                auditLogQueryService.recent(RECENT_AUDIT_LIMIT));
    }

    private Map<UUID, Long> attachmentCounts(List<UUID> courseworkIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (UUID id : courseworkIds) {
            counts.put(id, 0L);
        }
        if (courseworkIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : attachmentRepository.countGroupedByCourseworkId(courseworkIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static ClassHubUserDetails requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal;
    }
}
