package com.classhub.auth;

import com.classhub.user.UserRole;
import com.classhub.user.UserStatus;
import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserRole role,
        UserStatus status,
        boolean emailVerified) {
}
