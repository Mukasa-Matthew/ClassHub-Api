package com.classhub.user;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a user. Encodes {@link CreateUserCommand#rawPassword()} before persistence.
     */
    @Transactional
    public User create(CreateUserCommand command) {
        requireNonNull(command, "command");
        requireNonNull(command.role(), "role");
        requireNonNull(command.status(), "status");

        String firstName = requireText(command.firstName(), "firstName");
        String lastName = requireText(command.lastName(), "lastName");
        String email = normalizeEmail(command.email());
        String phoneNumber = normalizePhoneNumber(command.phoneNumber());
        String rawPassword = requireText(command.rawPassword(), "password");
        String passwordHash = passwordEncoder.encode(rawPassword);

        if (userRepository.existsByEmail(email)) {
            throw new ApplicationException(
                    ErrorCodes.USER_ALREADY_EXISTS,
                    "A user with this email already exists",
                    HttpStatus.CONFLICT);
        }

        String registrationNumber = normalizeRegistrationNumber(command.registrationNumber());
        if (registrationNumber != null
                && userRepository.existsByRegistrationNumberIgnoreCase(registrationNumber)) {
            throw new ApplicationException(
                    ErrorCodes.USER_ALREADY_EXISTS,
                    "A user with this registration number already exists",
                    HttpStatus.CONFLICT);
        }

        User user = new User(
                firstName,
                lastName,
                email,
                phoneNumber,
                passwordHash,
                command.role(),
                command.status(),
                command.emailVerified(),
                registrationNumber);

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ApplicationException(
                    ErrorCodes.USER_ALREADY_EXISTS,
                    "A user with this email already exists",
                    HttpStatus.CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public boolean existsByRegistrationNumber(String registrationNumber) {
        String normalized = normalizeRegistrationNumber(registrationNumber);
        return normalized != null && userRepository.existsByRegistrationNumberIgnoreCase(normalized);
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        String normalized = normalizeEmail(email);
        return userRepository.findByEmail(normalized)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.USER_NOT_FOUND,
                        "User not found",
                        HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User getById(java.util.UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.USER_NOT_FOUND,
                        "User not found",
                        HttpStatus.NOT_FOUND));
    }

    public static String normalizeEmail(String email) {
        String normalized = requireText(email, "email").toLowerCase();
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    "email is invalid",
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String trimmed = phoneNumber.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    public static String normalizeRegistrationNumber(String registrationNumber) {
        if (registrationNumber == null) {
            return null;
        }
        String trimmed = registrationNumber.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 64) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    "registrationNumber is too long",
                    HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    public static String requireRegistrationNumber(String registrationNumber) {
        String normalized = normalizeRegistrationNumber(registrationNumber);
        if (normalized == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    "registrationNumber is required",
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    field + " is required",
                    HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_USER_DATA,
                    field + " is required",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
