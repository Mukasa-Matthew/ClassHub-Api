package com.classhub.notification.push;

import com.classhub.common.api.ApiResponse;
import com.classhub.notification.config.NotificationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/push-config")
public class WebPushPublicConfigurationController {

    private final NotificationProperties properties;

    public WebPushPublicConfigurationController(NotificationProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<WebPushPublicConfigurationResponse> getConfiguration() {
        NotificationProperties.Push push = properties.getPush();
        boolean available = push.isEnabled() && push.isConfigured();
        return ApiResponse.of(new WebPushPublicConfigurationResponse(
                available, available ? push.getVapidPublicKey() : null));
    }
}
