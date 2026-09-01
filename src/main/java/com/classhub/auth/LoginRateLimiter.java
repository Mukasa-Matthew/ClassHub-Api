package com.classhub.auth;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Single-instance login attempt limiter. For multi-instance deployments, replace with a shared
 * store (e.g. Redis) later.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Window> attempts = new ConcurrentHashMap<>();

    public void check(String clientKey) {
        Instant now = Instant.now();
        Window window = attempts.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.windowStart().plus(WINDOW).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart(), existing.count() + 1);
        });

        if (window.count() > MAX_ATTEMPTS) {
            throw new ApplicationException(
                    ErrorCodes.AUTHENTICATION_FAILED,
                    "Too many login attempts. Try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Clears attempt windows. Intended for tests that share one application context. */
    public void reset() {
        attempts.clear();
    }

    private record Window(Instant windowStart, int count) {
    }
}
