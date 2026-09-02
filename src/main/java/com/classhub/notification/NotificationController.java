package com.classhub.notification;

import com.classhub.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        NotificationService.ApiPage result = notificationService.listOwn(read, page, size);
        return ApiResponse.of(result.data(), result.pagination());
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.of(notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {
        return ApiResponse.of(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    public ApiResponse<MarkAllReadResponse> markAllRead() {
        return ApiResponse.of(notificationService.markAllRead());
    }
}
