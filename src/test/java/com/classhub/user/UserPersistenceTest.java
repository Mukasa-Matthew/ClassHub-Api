package com.classhub.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.support.PostgresTestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class UserPersistenceTest {

    private static final String RAW_PASSWORD = "CorrectHorseBatteryStaple1!";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void userPersistsSuccessfullyWithUuidRoleStatusAndTimestamps() {
        Instant before = Instant.now().minusSeconds(1);

        User created = userService.create(new CreateUserCommand(
                "Ada",
                "Lovelace",
                "ada@example.com",
                null,
                RAW_PASSWORD,
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                false));

        Instant after = Instant.now().plusSeconds(1);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getId()).isInstanceOf(UUID.class);
        assertThat(created.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getPhoneNumber()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(created.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(created.getUpdatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);

        User reloaded = userRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(created.getId());
        assertThat(reloaded.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getCreatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    @Transactional
    void passwordIsEncodedAndPlaintextIsNeverStored() {
        User created = userService.create(new CreateUserCommand(
                "Hash",
                "Check",
                "hash@example.com",
                null,
                RAW_PASSWORD,
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                false));

        assertThat(created.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(created.getPasswordHash()).startsWith("$argon2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, created.getPasswordHash())).isTrue();

        String storedHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?",
                String.class,
                created.getId());
        assertThat(storedHash).isNotEqualTo(RAW_PASSWORD);
        assertThat(storedHash).isEqualTo(created.getPasswordHash());
    }

    @Test
    @Transactional
    void emailIsNormalizedBeforePersistence() {
        User created = userService.create(new CreateUserCommand(
                "Matthew",
                "Tester",
                "  Matthew@Example.COM ",
                "  +256700000000  ",
                RAW_PASSWORD,
                UserRole.CLASS_REP,
                UserStatus.ACTIVE,
                true));

        assertThat(created.getEmail()).isEqualTo("matthew@example.com");
        assertThat(created.getPhoneNumber()).isEqualTo("+256700000000");
        assertThat(userRepository.findByEmail("matthew@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("matthew@example.com")).isTrue();
    }

    @Test
    @Transactional
    void duplicateEmailIsRejectedByService() {
        userService.create(new CreateUserCommand(
                "One",
                "User",
                "dup@example.com",
                null,
                RAW_PASSWORD,
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                false));

        assertThatThrownBy(() -> userService.create(new CreateUserCommand(
                        "Two",
                        "User",
                        "DUP@example.com",
                        null,
                        RAW_PASSWORD,
                        UserRole.STUDENT,
                        UserStatus.ACTIVE,
                        false)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> {
                    ApplicationException appEx = (ApplicationException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCodes.USER_ALREADY_EXISTS);
                });
    }

    @Test
    @Transactional
    void databaseUniquenessProtectsAgainstDuplicateEmail() {
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        String hash = passwordEncoder.encode(RAW_PASSWORD);
        jdbcTemplate.update(
                """
                insert into users
                (id, first_name, last_name, email, phone_number, password_hash, role, status, email_verified, created_at, updated_at)
                values (?, ?, ?, ?, null, ?, ?, ?, false, ?, ?)
                """,
                UUID.randomUUID(),
                "Raw",
                "One",
                "rawdup@example.com",
                hash,
                UserRole.STUDENT.name(),
                UserStatus.ACTIVE.name(),
                now,
                now);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into users
                        (id, first_name, last_name, email, phone_number, password_hash, role, status, email_verified, created_at, updated_at)
                        values (?, ?, ?, ?, null, ?, ?, ?, false, ?, ?)
                        """,
                        UUID.randomUUID(),
                        "Raw",
                        "Two",
                        "RAWDUP@example.com",
                        hash,
                        UserRole.STUDENT.name(),
                        UserStatus.ACTIVE.name(),
                        now,
                        now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void rolesAndStatusesPersistAndAreQueryAble() {
        userService.create(new CreateUserCommand(
                "Admin",
                "User",
                "admin@example.com",
                null,
                RAW_PASSWORD,
                UserRole.SUPER_ADMIN,
                UserStatus.ACTIVE,
                true));
        userService.create(new CreateUserCommand(
                "Suspended",
                "Student",
                "suspended@example.com",
                null,
                RAW_PASSWORD,
                UserRole.STUDENT,
                UserStatus.SUSPENDED,
                false));
        userService.create(new CreateUserCommand(
                "Disabled",
                "Rep",
                "disabled@example.com",
                null,
                RAW_PASSWORD,
                UserRole.CLASS_REP,
                UserStatus.DISABLED,
                false));

        List<User> admins = userRepository.findByRole(UserRole.SUPER_ADMIN);
        List<User> suspended = userRepository.findByStatus(UserStatus.SUSPENDED);

        assertThat(admins).extracting(User::getEmail).containsExactly("admin@example.com");
        assertThat(suspended).extracting(User::getEmail).containsExactly("suspended@example.com");
        assertThat(userRepository.findByRole(UserRole.CLASS_REP))
                .extracting(User::getStatus)
                .containsExactly(UserStatus.DISABLED);
    }

    @Test
    @Transactional
    void blankPhoneBecomesNullAndLookupByEmailWorks() {
        User created = userService.create(new CreateUserCommand(
                "Phone",
                "User",
                "phone@example.com",
                "   ",
                RAW_PASSWORD,
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                false));

        assertThat(created.getPhoneNumber()).isNull();
        assertThat(userService.getByEmail("PHONE@example.com").getId()).isEqualTo(created.getId());
    }

    @Test
    @Transactional
    void invalidUserDataIsRejected() {
        assertThatThrownBy(() -> userService.create(new CreateUserCommand(
                        " ",
                        "User",
                        "bad@example.com",
                        null,
                        RAW_PASSWORD,
                        UserRole.STUDENT,
                        UserStatus.ACTIVE,
                        false)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode())
                        .isEqualTo(ErrorCodes.INVALID_USER_DATA));
    }
}
