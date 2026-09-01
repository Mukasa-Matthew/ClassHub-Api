package com.classhub.notification;

import com.classhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<NotificationPreferenceResponse> getOwn() {
        return ApiResponse.of(preferenceService.getOwn());
    }

    @PutMapping
    public ApiResponse<NotificationPreferenceResponse> updateOwn(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return ApiResponse.of(preferenceService.updateOwn(request));
    }
}
