package com.classhub.auth;

import com.classhub.user.CreateUserCommand;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Creates the initial SUPER_ADMIN when bootstrap credentials are provided and none exists yet.
 *
 * <p>Disable after first setup by clearing {@code BOOTSTRAP_ADMIN_EMAIL} and
 * {@code BOOTSTRAP_ADMIN_PASSWORD} (or omitting them). Bootstrap never overwrites an existing admin.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            UserRepository userRepository,
            UserService userService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.debug("Bootstrap admin skipped: credentials not configured");
            return;
        }
        if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            log.info("Bootstrap admin skipped: SUPER_ADMIN already exists");
            return;
        }

        userService.create(new CreateUserCommand(
                properties.getFirstName(),
                properties.getLastName(),
                properties.getEmail(),
                null,
                properties.getPassword(),
                UserRole.SUPER_ADMIN,
                UserStatus.ACTIVE,
                true));

        log.info("Bootstrap SUPER_ADMIN created for email={}", UserService.normalizeEmail(properties.getEmail()));
    }
}
