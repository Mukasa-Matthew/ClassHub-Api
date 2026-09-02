package com.classhub.notification.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeletePushSubscriptionRequest(@NotBlank @Size(max = 2048) String endpoint) {}
