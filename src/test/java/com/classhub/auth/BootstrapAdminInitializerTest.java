package com.classhub.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class BootstrapAdminInitializerTest {

    @DynamicPropertySource
    static void bootstrapProperties(DynamicPropertyRegistry registry) {
        registry.add("classhub.bootstrap.admin.email", () -> "bootstrap.admin@example.com");
        registry.add("classhub.bootstrap.admin.password", () -> "BootstrapPassw0rd!");
        registry.add("classhub.bootstrap.admin.first-name", () -> "Bootstrap");
        registry.add("classhub.bootstrap.admin.last-name", () -> "Admin");
    }

    @Autowired
    private BootstrapAdminInitializer bootstrapAdminInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void bootstrapCreatesAdminWhenAbsentAndDoesNotDuplicateOnRerun() {
        assertThat(userRepository.existsByRole(UserRole.SUPER_ADMIN)).isTrue();
        assertThat(userRepository.findByEmail("bootstrap.admin@example.com")).isPresent();

        long adminCountBefore = userRepository.findByRole(UserRole.SUPER_ADMIN).size();
        String hashBefore = userRepository.findByEmail("bootstrap.admin@example.com")
                .orElseThrow()
                .getPasswordHash();

        bootstrapAdminInitializer.run(new DefaultApplicationArguments());

        assertThat(userRepository.findByRole(UserRole.SUPER_ADMIN)).hasSize((int) adminCountBefore);
        assertThat(userRepository.findByEmail("bootstrap.admin@example.com").orElseThrow().getPasswordHash())
                .isEqualTo(hashBefore);
        assertThat(passwordEncoder.matches(
                        "BootstrapPassw0rd!",
                        userRepository.findByEmail("bootstrap.admin@example.com").orElseThrow().getPasswordHash()))
                .isTrue();
        assertThat(hashBefore).isNotEqualTo("BootstrapPassw0rd!");
        assertThat(hashBefore).startsWith("$argon2");
    }
}
