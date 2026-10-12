package com.classhub.auth;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordResetChallengeRepository challengeRepository;
    private final PasswordResetSecretFactory secretFactory;
    private final PasswordResetNotificationService notificationService;
    private final PasswordResetRateLimiter rateLimiter;
    private final UserSessionInvalidator sessionInvalidator;
    private final NotificationDeliveryRepository deliveryRepository;
    private final Clock clock;
    private final Duration otpTtl;
    private final Duration resetTokenTtl;
    private final Duration resendCooldown;
    private final int maximumAttempts;

    public PasswordRecoveryService(
            UserRepository userRepository,
            UserService userService,
            PasswordResetChallengeRepository challengeRepository,
            PasswordResetSecretFactory secretFactory,
            PasswordResetNotificationService notificationService,
            PasswordResetRateLimiter rateLimiter,
            UserSessionInvalidator sessionInvalidator,
            NotificationDeliveryRepository deliveryRepository,
            Clock clock,
            @Value("${classhub.auth.password-reset.otp-ttl:PT10M}") Duration otpTtl,
            @Value("${classhub.auth.password-reset.reset-token-ttl:PT10M}") Duration resetTokenTtl,
            @Value("${classhub.auth.password-reset.resend-cooldown:PT1M}") Duration resendCooldown,
            @Value("${classhub.auth.password-reset.maximum-attempts:5}") int maximumAttempts) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.challengeRepository = challengeRepository;
        this.secretFactory = secretFactory;
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
        this.sessionInvalidator = sessionInvalidator;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
        this.otpTtl = otpTtl;
        this.resetTokenTtl = resetTokenTtl;
        this.resendCooldown = resendCooldown;
        this.maximumAttempts = maximumAttempts;
    }

    @Transactional
    public PasswordRecoveryResponse forgotPassword(String identifier, String clientKey) {
        String normalized = normalizeIdentifier(identifier);
        rateLimiter.check(clientKey + ":" + Integer.toHexString(normalized.hashCode()));
        User user = findUser(normalized).filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE).orElse(null);
        if (user == null || !hasRecoveryChannel(user)) {
            return PasswordRecoveryResponse.accepted();
        }

        Instant now = clock.instant();
        Optional<PasswordResetChallenge> latest =
                challengeRepository.findFirstByUserIdAndSupersededAtIsNullOrderByRequestedAtDesc(user.getId());
        if (latest.isPresent() && now.isBefore(latest.get().getRequestedAt().plus(resendCooldown))) {
            return PasswordRecoveryResponse.accepted();
        }
        latest.ifPresent(challenge -> {
            challenge.supersede(now);
            deliveryRepository.skipPendingPasswordResetDeliveries(challenge.getId(), now);
        });

        UUID challengeId = UUID.randomUUID();
        String otp = secretFactory.otp(challengeId, now);
        PasswordResetChallenge challenge = challengeRepository.saveAndFlush(new PasswordResetChallenge(
                challengeId, user, secretFactory.hashOtp(challengeId, otp), now, now.plus(otpTtl)));
        notificationService.queueOtp(user, challenge);
        return PasswordRecoveryResponse.accepted();
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public PasswordResetAuthorizationResponse verifyOtp(String identifier, String otp) {
        User user = findUser(normalizeIdentifier(identifier)).orElseThrow(this::invalidOtp);
        PasswordResetChallenge challenge = challengeRepository
                .findFirstByUserIdAndSupersededAtIsNullOrderByRequestedAtDesc(user.getId())
                .orElseThrow(this::invalidOtp);
        Instant now = clock.instant();
        if (!challenge.isOtpUsableAt(now, maximumAttempts)) {
            throw invalidOtp();
        }
        if (!secretFactory.matchesOtp(challenge, otp)) {
            challenge.recordFailedAttempt();
            throw invalidOtp();
        }
        String resetToken = secretFactory.newResetToken();
        challenge.verify(now, secretFactory.hashResetToken(resetToken), now.plus(resetTokenTtl));
        deliveryRepository.skipPendingPasswordResetDeliveries(challenge.getId(), now);
        return new PasswordResetAuthorizationResponse(resetToken);
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        PasswordResetChallenge challenge = challengeRepository
                .findByResetTokenHash(secretFactory.hashResetToken(resetToken))
                .orElseThrow(this::invalidResetToken);
        Instant now = clock.instant();
        if (challenge.getOtpVerifiedAt() == null
                || challenge.getResetUsedAt() != null
                || challenge.getSupersededAt() != null
                || challenge.getResetTokenExpiresAt() == null
                || !now.isBefore(challenge.getResetTokenExpiresAt())) {
            throw invalidResetToken();
        }
        User user = challenge.getUser();
        userService.changePassword(user, newPassword);
        challenge.consumeReset(now);
        challengeRepository.findByUserIdAndSupersededAtIsNullAndResetUsedAtIsNull(user.getId())
                .forEach(other -> {
                    other.supersede(now);
                    deliveryRepository.skipPendingPasswordResetDeliveries(other.getId(), now);
                });
        sessionInvalidator.invalidateAll(user.getId());
        notificationService.queuePasswordChanged(user, challenge);
    }

    private Optional<User> findUser(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailForPasswordReset(identifier);
        }
        return userRepository.findByPhoneNumberForPasswordReset(identifier);
    }

    private static boolean hasRecoveryChannel(User user) {
        return (user.isEmailVerified() && user.getEmail() != null && !user.getEmail().isBlank())
                || (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank());
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private ApplicationException invalidOtp() {
        return new ApplicationException(
                ErrorCodes.INVALID_PASSWORD_RESET_OTP,
                "The verification code is invalid or expired",
                HttpStatus.BAD_REQUEST);
    }

    private ApplicationException invalidResetToken() {
        return new ApplicationException(
                ErrorCodes.INVALID_PASSWORD_RESET_TOKEN,
                "The password reset authorization is invalid or expired",
                HttpStatus.BAD_REQUEST);
    }
}
