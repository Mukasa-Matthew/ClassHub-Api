package com.classhub.announcement;

import com.classhub.academicclass.AcademicClass;
import com.classhub.academicclass.AcademicClassService;
import com.classhub.academicclass.ClassMembershipAccessService;
import com.classhub.audit.AuditAction;
import com.classhub.audit.AuditEntityTypes;
import com.classhub.audit.AuditService;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.notification.NotificationService;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ClassMembershipAccessService membershipAccessService;
    private final AcademicClassService academicClassService;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditService auditService,
            ClassMembershipAccessService membershipAccessService,
            AcademicClassService academicClassService) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.membershipAccessService = membershipAccessService;
        this.academicClassService = academicClassService;
    }

    @Transactional
    public AnnouncementResponse create(CreateAnnouncementRequest request) {
        String title = requireText(request.title(), "title");
        String content = requireText(request.content(), "content");
        User creator = requireCurrentUser();
        AcademicClass academicClass = resolveClassForCreate(creator, request.classId());
        Announcement announcement = new Announcement(title, content, creator, academicClass);
        Announcement saved = announcementRepository.saveAndFlush(announcement);
        auditService.record(
                AuditAction.ANNOUNCEMENT_CREATED,
                AuditEntityTypes.ANNOUNCEMENT,
                saved.getId(),
                "Created draft announcement \"" + saved.getTitle() + "\"");
        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public AnnouncementResponse update(UUID id, UpdateAnnouncementRequest request) {
        Announcement announcement = requireAnnouncement(id);
        enforceClassManagementAccess(announcement);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw invalidState("Only DRAFT announcements can be updated");
        }
        String title = request.title() == null
                ? announcement.getTitle()
                : requireText(request.title(), "title");
        String content = request.content() == null
                ? announcement.getContent()
                : requireText(request.content(), "content");
        announcement.updateContent(title, content);
        Announcement saved = announcementRepository.saveAndFlush(announcement);
        auditService.record(
                AuditAction.ANNOUNCEMENT_UPDATED,
                AuditEntityTypes.ANNOUNCEMENT,
                saved.getId(),
                "Updated draft announcement \"" + saved.getTitle() + "\"");
        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public AnnouncementResponse publish(UUID id) {
        Announcement announcement = requireAnnouncement(id);
        enforceClassManagementAccess(announcement);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw invalidState("Only DRAFT announcements can be published");
        }
        announcement.publish(Instant.now());
        Announcement saved = announcementRepository.saveAndFlush(announcement);
        notificationService.notifyAnnouncementPublished(saved);
        auditService.record(
                AuditAction.ANNOUNCEMENT_PUBLISHED,
                AuditEntityTypes.ANNOUNCEMENT,
                saved.getId(),
                "Published announcement \"" + saved.getTitle() + "\"");
        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public AnnouncementResponse archive(UUID id) {
        Announcement announcement = requireAnnouncement(id);
        enforceClassManagementAccess(announcement);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw invalidState("Only PUBLISHED announcements can be archived");
        }
        announcement.archive();
        Announcement saved = announcementRepository.saveAndFlush(announcement);
        auditService.record(
                AuditAction.ANNOUNCEMENT_ARCHIVED,
                AuditEntityTypes.ANNOUNCEMENT,
                saved.getId(),
                "Archived announcement \"" + saved.getTitle() + "\"");
        return AnnouncementResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getById(UUID id) {
        Announcement announcement = requireAnnouncement(id);
        if (currentRole() == UserRole.STUDENT && announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw notFound();
        }
        enforceClassReadAccess(announcement);
        return AnnouncementResponse.from(announcement);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list() {
        UserRole role = currentRole();
        if (role == UserRole.STUDENT) {
            UUID classId = membershipAccessService.requireActiveClassId(currentPrincipal().getId());
            return announcementRepository.findAllDetailedByClass(classId, AnnouncementStatus.PUBLISHED).stream()
                    .map(AnnouncementResponse::from)
                    .toList();
        }
        if (role == UserRole.CLASS_REP) {
            UUID classId = membershipAccessService.requireClassRepMembership(currentPrincipal().getId())
                    .getAcademicClass()
                    .getId();
            return announcementRepository.findAllDetailedByClass(classId, null).stream()
                    .map(AnnouncementResponse::from)
                    .toList();
        }
        return announcementRepository.findAllDetailed(null).stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    private AcademicClass resolveClassForCreate(User creator, UUID classId) {
        UserRole role = creator.getRole();
        if (role == UserRole.CLASS_REP) {
            return membershipAccessService.requireClassRepMembership(creator.getId()).getAcademicClass();
        }
        if (role == UserRole.SUPER_ADMIN) {
            if (classId == null) {
                throw new ApplicationException(
                        ErrorCodes.INVALID_ANNOUNCEMENT_DATA,
                        "classId is required",
                        HttpStatus.BAD_REQUEST);
            }
            return academicClassService.requireClass(classId);
        }
        throw new ApplicationException(ErrorCodes.FORBIDDEN, "Forbidden", HttpStatus.FORBIDDEN);
    }

    private Announcement requireAnnouncement(UUID id) {
        return announcementRepository.findDetailedById(id).orElseThrow(this::notFound);
    }

    private void enforceClassManagementAccess(Announcement announcement) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        if (role == UserRole.CLASS_REP) {
            membershipAccessService.requireClassRepForClass(
                    currentPrincipal().getId(), announcement.getAcademicClass().getId());
            return;
        }
        throw new ApplicationException(ErrorCodes.FORBIDDEN, "Forbidden", HttpStatus.FORBIDDEN);
    }

    private void enforceClassReadAccess(Announcement announcement) {
        UserRole role = currentRole();
        if (role == UserRole.SUPER_ADMIN) {
            return;
        }
        membershipAccessService.requireCanAccessClass(
                currentPrincipal().getId(), announcement.getAcademicClass().getId());
    }

    private User requireCurrentUser() {
        ClassHubUserDetails principal = currentPrincipal();
        return userRepository
                .findById(principal.getId())
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.UNAUTHENTICATED,
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ANNOUNCEMENT_DATA,
                    field + " is required",
                    HttpStatus.BAD_REQUEST);
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_ANNOUNCEMENT_DATA,
                    field + " is required",
                    HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private static ApplicationException invalidState(String message) {
        return new ApplicationException(
                ErrorCodes.INVALID_ANNOUNCEMENT_STATE, message, HttpStatus.CONFLICT);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                ErrorCodes.ANNOUNCEMENT_NOT_FOUND,
                "Announcement not found",
                HttpStatus.NOT_FOUND);
    }

    private static UserRole currentRole() {
        return currentPrincipal().getRole();
    }

    private static ClassHubUserDetails currentPrincipal() {
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
