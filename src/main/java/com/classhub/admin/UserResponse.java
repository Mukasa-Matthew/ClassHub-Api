package com.classhub.admin;

import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
