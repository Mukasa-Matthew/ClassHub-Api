package com.classhub.notification.push;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.User;
import com.classhub.user.UserRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {

    private static final int P256DH_BYTES = 65;
    private static final int AUTH_BYTES = 16;

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public PushSubscriptionService(
            PushSubscriptionRepository subscriptionRepository, UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PushSubscriptionStatusResponse register(RegisterPushSubscriptionRequest request) {
        User user = currentUser();
        String endpoint = validateEndpoint(request.endpoint());
        String p256dh = validateKey(request.keys().p256dh(), P256DH_BYTES, "p256dh");
        String auth = validateKey(request.keys().auth(), AUTH_BYTES, "auth");
        String endpointHash = hash(endpoint);

        PushSubscription existing = subscriptionRepository.findByEndpointHash(endpointHash).orElse(null);
        if (existing != null) {
            if (!existing.getEndpoint().equals(endpoint) || !existing.getUser().getId().equals(user.getId())) {
                throw conflict();
            }
            existing.updateKeys(p256dh, auth);
            subscriptionRepository.saveAndFlush(existing);
            return statusFor(user, endpointHash);
        }

        try {
            subscriptionRepository.saveAndFlush(
                    new PushSubscription(user, endpoint, endpointHash, p256dh, auth));
        } catch (DataIntegrityViolationException ex) {
            throw conflict();
        }
        return statusFor(user, endpointHash);
    }

    @Transactional
    public void remove(DeletePushSubscriptionRequest request) {
        User user = currentUser();
        String endpoint = validateEndpoint(request.endpoint());
        String endpointHash = hash(endpoint);
        subscriptionRepository.findByEndpointHash(endpointHash)
                .filter(subscription -> subscription.getEndpoint().equals(endpoint))
                .filter(subscription -> subscription.getUser().getId().equals(user.getId()))
                .ifPresent(subscriptionRepository::delete);
    }

    @Transactional(readOnly = true)
    public PushSubscriptionStatusResponse status(String endpoint) {
        User user = currentUser();
        if (endpoint == null || endpoint.isBlank()) {
            return new PushSubscriptionStatusResponse(
                    subscriptionRepository.existsByUserId(user.getId()),
                    subscriptionRepository.countByUserId(user.getId()));
        }
        return statusFor(user, hash(validateEndpoint(endpoint)));
    }

    private PushSubscriptionStatusResponse statusFor(User user, String endpointHash) {
        return new PushSubscriptionStatusResponse(
                subscriptionRepository.existsByUserIdAndEndpointHash(user.getId(), endpointHash),
                subscriptionRepository.countByUserId(user.getId()));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ClassHubUserDetails principal)) {
            throw new ApplicationException(
                    ErrorCodes.UNAUTHENTICATED, "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findById(principal.getId()).orElseThrow(() -> new ApplicationException(
                ErrorCodes.UNAUTHENTICATED, "Authentication required", HttpStatus.UNAUTHORIZED));
    }

    private static String validateEndpoint(String value) {
        String endpoint = value.trim();
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw invalid("endpoint");
            }
            return endpoint;
        } catch (IllegalArgumentException ex) {
            throw invalid("endpoint");
        }
    }

    private static String validateKey(String value, int expectedBytes, String field) {
        String key = value.trim();
        try {
            if (Base64.getUrlDecoder().decode(key).length != expectedBytes) {
                throw invalid(field);
            }
            return key;
        } catch (IllegalArgumentException ex) {
            throw invalid(field);
        }
    }

    private static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static ApplicationException invalid(String field) {
        return new ApplicationException(
                ErrorCodes.INVALID_PUSH_SUBSCRIPTION,
                "Push subscription " + field + " is invalid",
                HttpStatus.BAD_REQUEST);
    }

    private static ApplicationException conflict() {
        return new ApplicationException(
                ErrorCodes.PUSH_SUBSCRIPTION_CONFLICT,
                "Push subscription could not be registered",
                HttpStatus.CONFLICT);
    }
}
