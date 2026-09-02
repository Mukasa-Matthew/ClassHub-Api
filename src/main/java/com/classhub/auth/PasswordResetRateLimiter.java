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
public class PasswordResetRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Map<String, Window> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    public PasswordResetRateLimiter(Clock clock) { this.clock = clock; }

    public void check(String key) {
        Instant now = clock.instant();
        Window window = requests.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
        if (window.count() > MAX_REQUESTS) {
            throw new ApplicationException(
                    ErrorCodes.PASSWORD_RESET_RATE_LIMITED,
                    "Too many password recovery requests. Try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void reset() { requests.clear(); }

    private record Window(Instant startedAt, int count) {}
}
