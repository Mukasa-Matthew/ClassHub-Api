package com.classhub.user;

/**
 * Command for creating a user.
 *
 * <p>{@code rawPassword} is plaintext only within this command boundary. {@link UserService}
 * encodes it with the configured {@code PasswordEncoder} before persistence. Callers must never
 * write plaintext into {@code users.password_hash}.
 */
public record CreateUserCommand(
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String rawPassword,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        String registrationNumber) {

    public CreateUserCommand(
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String rawPassword,
            UserRole role,
            UserStatus status,
            boolean emailVerified) {
        this(
                firstName,
                lastName,
                email,
                phoneNumber,
                rawPassword,
                role,
                status,
                emailVerified,
                null);
    }
}
