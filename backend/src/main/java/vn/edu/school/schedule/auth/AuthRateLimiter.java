package vn.edu.school.schedule.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.school.schedule.shared.api.ApiException;

@Component
public class AuthRateLimiter {
    private final Map<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int perMinute;
    private final int perHour;

    @Autowired
    public AuthRateLimiter(@Value("${app.security.auth-rate-limit-per-minute:5}") int perMinute,
                           @Value("${app.security.auth-rate-limit-per-hour:30}") int perHour) {
        this(Clock.systemUTC(), perMinute, perHour);
    }
    AuthRateLimiter(Clock clock) { this(clock, 5, 30); }
    AuthRateLimiter(Clock clock, int perMinute, int perHour) {
        if (perMinute < 1 || perHour < perMinute) throw new IllegalArgumentException("Invalid authentication rate limit.");
        this.clock = clock;
        this.perMinute = perMinute;
        this.perHour = perHour;
    }

    public void check(String key) {
        Instant now = clock.instant();
        ArrayDeque<Instant> values = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (values) {
            while (!values.isEmpty() && values.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) values.removeFirst();
            long minuteCount = values.stream().filter(value -> !value.isBefore(now.minus(Duration.ofMinutes(1)))).count();
            if (minuteCount >= perMinute || values.size() >= perHour) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Quá nhiều yêu cầu. Vui lòng thử lại sau.");
            }
            values.addLast(now);
        }
    }
}
