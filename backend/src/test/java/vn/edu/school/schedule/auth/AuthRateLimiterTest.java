package vn.edu.school.schedule.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.edu.school.schedule.shared.api.ApiException;

class AuthRateLimiterTest {
    @Test
    void rejectsSixthAttemptWithinOneMinute() {
        AuthRateLimiter limiter = new AuthRateLimiter(
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        for (int attempt = 0; attempt < 5; attempt++) limiter.check("login:client");

        assertThatThrownBy(() -> limiter.check("login:client"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("RATE_LIMITED");
    }
}
