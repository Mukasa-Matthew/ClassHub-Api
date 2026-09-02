package com.classhub.auth;

import com.classhub.common.api.ErrorCodes;
import com.classhub.common.exception.ApplicationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ClassRepOnboardingRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Map<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public ClassRepOnboardingRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key) {
        Instant now = clock.instant();
        Window window = attempts.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.startedAt().plus(WINDOW))) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt(), existing.count() + 1);
        });
        if (window.count() > MAX_ATTEMPTS) {
            throw new ApplicationException(
                    ErrorCodes.ONBOARDING_RATE_LIMITED,
                    "Too many account setup requests. Try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void reset() { attempts.clear(); }

    private record Window(Instant startedAt, int count) {
    }
}
