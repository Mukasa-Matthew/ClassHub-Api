package com.classhub.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 64) String registrationNumber,
        @NotBlank @Size(max = 32) String phoneNumber,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 4, max = 16) String classJoinCode) {
}
