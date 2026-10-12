package com.classhub.auth;

import com.classhub.academicclass.AcademicClassResponse;
import com.classhub.academicclass.AcademicClassService;
import com.classhub.academicclass.CreateAcademicClassRequest;
import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationOrchestrator;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassRepOnboardingService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ClassRepAccountSetupRepository setupRepository;
    private final ClassRepSetupTokenFactory tokenFactory;
    private final ClassRepSetupNotificationService notificationService;
    private final NotificationDeliveryRepository deliveryRepository;
    private final AcademicClassService academicClassService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ClassRepOnboardingRateLimiter rateLimiter;
    private final Clock clock;
    private final Duration tokenTtl;

    public ClassRepOnboardingService(
            UserService userService,
            UserRepository userRepository,
            ClassRepAccountSetupRepository setupRepository,
            ClassRepSetupTokenFactory tokenFactory,
            ClassRepSetupNotificationService notificationService,
            NotificationDeliveryRepository deliveryRepository,
            AcademicClassService academicClassService,
            NotificationOrchestrator notificationOrchestrator,
            ClassRepOnboardingRateLimiter rateLimiter,
            Clock clock,
            @Value("${classhub.auth.class-rep-setup.token-ttl:PT24H}") Duration tokenTtl) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.setupRepository = setupRepository;
        this.tokenFactory = tokenFactory;
        this.notificationService = notificationService;
        this.deliveryRepository = deliveryRepository;
        this.academicClassService = academicClassService;
        this.notificationOrchestrator = notificationOrchestrator;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
    }

    @Transactional
    public ClassRepOnboardingResponse register(ClassRepRegistrationRequest request, String clientKey) {
        String email = UserService.normalizeEmail(request.email());
        rateLimiter.check("register:" + clientKey + ":" + email);
        User user = userService.createPendingClassRep(
                request.firstName(), request.lastName(), email, request.phoneNumber());
        issue(user);
        return ClassRepOnboardingResponse.accepted();
    }

    @Transactional
    public ClassRepOnboardingResponse reissue(ClassRepSetupLinkReissueRequest request, String clientKey) {
        String email = UserService.normalizeEmail(request.email());
        String phone = UserService.requirePhoneNumber(request.phoneNumber());
        rateLimiter.check("reissue:" + clientKey + ":" + email);
        User user = userRepository.findByEmailAndPhoneNumber(email, phone)
                .filter(candidate -> candidate.getRole() == UserRole.CLASS_REP
                        && candidate.getStatus() == UserStatus.PENDING_SETUP)
                .orElse(null);
        if (user == null) {
            return ClassRepOnboardingResponse.accepted();
        }
        Instant now = clock.instant();
        for (ClassRepAccountSetup existing
                : setupRepository.findByUserIdAndUsedAtIsNullAndSupersededAtIsNull(user.getId())) {
            existing.supersede(now);
            deliveryRepository.skipPendingAccountSetupDeliveries(existing.getId(), now);
        }
        issue(user);
        return ClassRepOnboardingResponse.accepted();
    }

    @Transactional
    public AcademicClassResponse complete(ClassRepSetupRequest request) {
        Instant now = clock.instant();
        ClassRepAccountSetup setup = setupRepository.findByTokenHash(tokenFactory.hash(request.token()))
                .orElseThrow(this::invalidToken);
        if (setup.getUsedAt() != null || setup.getSupersededAt() != null) {
            throw invalidToken();
        }
        if (!now.isBefore(setup.getExpiresAt())) {
            throw new ApplicationException(
                    ErrorCodes.EXPIRED_ACCOUNT_SETUP_TOKEN,
                    "Account setup link has expired",
                    HttpStatus.GONE);
        }
        User user = setup.getUser();
        if (user.getStatus() != UserStatus.PENDING_SETUP || user.getRole() != UserRole.CLASS_REP) {
            throw invalidToken();
        }

        userService.completeAccountSetup(user, request.password());
        String programmeName = request.programmeName().trim();
        AcademicClassResponse academicClass = academicClassService.create(new CreateAcademicClassRequest(
                programmeName + " Year " + request.studyYear(),
                programmeName,
                null,
                request.studyYear(),
                request.semester(),
                request.academicYear()));
        academicClassService.assignClassRepresentative(academicClass.id(), user.getId());
        setup.markUsed(now);
        deliveryRepository.skipPendingAccountSetupDeliveries(setup.getId(), now);
        notificationOrchestrator.onAccountSetupCompleted(user, academicClassService.requireClass(academicClass.id()));
        return academicClass;
    }

    private void issue(User user) {
        Instant issuedAt = clock.instant();
        UUID issuanceId = UUID.randomUUID();
        String token = tokenFactory.create(issuanceId, issuedAt);
        ClassRepAccountSetup setup = setupRepository.saveAndFlush(new ClassRepAccountSetup(
                issuanceId, user, tokenFactory.hash(token), issuedAt, issuedAt.plus(tokenTtl)));
        notificationService.queue(user, setup);
    }

    private ApplicationException invalidToken() {
        return new ApplicationException(
                ErrorCodes.INVALID_ACCOUNT_SETUP_TOKEN,
                "Account setup link is invalid or has already been used",
                HttpStatus.BAD_REQUEST);
    }

}
