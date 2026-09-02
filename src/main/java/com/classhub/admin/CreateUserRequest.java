package com.classhub.admin;

import com.classhub.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 32) String phoneNumber,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull UserRole role) {
}
