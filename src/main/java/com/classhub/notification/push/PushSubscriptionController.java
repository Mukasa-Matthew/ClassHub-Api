package com.classhub.notification.push;

import com.classhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/push-subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService subscriptionService;

    public PushSubscriptionController(PushSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ApiResponse<PushSubscriptionStatusResponse> register(
            @Valid @RequestBody RegisterPushSubscriptionRequest request) {
        return ApiResponse.of(subscriptionService.register(request));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Valid @RequestBody DeletePushSubscriptionRequest request) {
        subscriptionService.remove(request);
    }

    @GetMapping("/status")
    public ApiResponse<PushSubscriptionStatusResponse> status(
            @RequestParam(required = false) String endpoint) {
        return ApiResponse.of(subscriptionService.status(endpoint));
    }
}
