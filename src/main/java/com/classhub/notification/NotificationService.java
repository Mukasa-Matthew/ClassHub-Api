package com.classhub.notification;

import com.classhub.announcement.Announcement;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.api.Pagination;
import com.classhub.common.exception.ApplicationException;
import com.classhub.coursework.Coursework;
import com.classhub.security.ClassHubUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final NotificationOrchestrator orchestrator;
    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationOrchestrator orchestrator, NotificationRepository notificationRepository) {
        this.orchestrator = orchestrator;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifyCourseworkPublished(Coursework coursework) {
        orchestrator.onCourseworkPublished(coursework);
    }

    @Transactional
    public void notifyAnnouncementPublished(Announcement announcement) {
        orchestrator.onAnnouncementPublished(announcement);
    }

    @Transactional(readOnly = true)
    public ApiPage listOwn(Boolean read, Integer page, Integer size) {
        UUID recipientId = currentUserId();
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 1) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_NOTIFICATION_DATA,
                    "page must be >= 1",
                    HttpStatus.BAD_REQUEST);
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_NOTIFICATION_DATA,
                    "size must be between 1 and " + MAX_SIZE,
                    HttpStatus.BAD_REQUEST);
        }

        Page<Notification> result = notificationRepository.findInbox(
                recipientId, read, PageRequest.of(pageNumber - 1, pageSize));
        List<NotificationResponse> data =
                result.getContent().stream().map(NotificationResponse::from).toList();
        Pagination pagination = new Pagination(
                pageNumber, pageSize, result.getTotalElements(), result.getTotalPages());
        return new ApiPage(data, pagination);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(
                notificationRepository.countByRecipientIdAndReadFalse(currentUserId()));
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(id, currentUserId())
                .orElseThrow(this::notFound);
        notification.markRead(Instant.now());
        return NotificationResponse.from(notificationRepository.saveAndFlush(notification));
    }

    @Transactional
    public MarkAllReadResponse markAllRead() {
        int updated = notificationRepository.markAllReadForRecipient(currentUserId(), Instant.now());
        return new MarkAllReadResponse(updated);
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED,
                    "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return principal.getId();
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                ErrorCodes.NOTIFICATION_NOT_FOUND,
                "Notification not found",
                HttpStatus.NOT_FOUND);
    }

    public record ApiPage(List<NotificationResponse> data, Pagination pagination) {
    }
}
