package com.classhub.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 512) String resetToken,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {}
