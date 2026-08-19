package vn.edu.school.schedule.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.auth.AuthService;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.security.SecurityHeaders;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final String cookieName;
    private final boolean cookieSecure;

    public AuthController(AuthService auth,
                          @Value("${app.security.cookie-name}") String cookieName,
                          @Value("${app.security.cookie-secure}") boolean cookieSecure) {
        this.auth = auth;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<RegistrationResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                                HttpServletRequest servletRequest) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                auth.register(request, servletRequest.getRemoteAddr()), correlationId()));
    }

    @PostMapping("/login")
    ApiResponse<CurrentUser> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                   HttpServletResponse response) {
        LoginResult result = auth.login(request, servletRequest.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.sessionToken(), Duration.ofHours(24)).toString());
        response.setHeader(SecurityHeaders.CSRF, result.csrfToken());
        return ApiResponse.success(result.user(), correlationId());
    }

    @GetMapping("/me")
    ApiResponse<CurrentUser> me(@AuthenticationPrincipal AuthenticatedUser user, HttpServletResponse response) {
        response.setHeader(SecurityHeaders.CSRF, auth.rotateCsrf(user));
        return ApiResponse.success(auth.loadCurrentUser(user.id()), correlationId());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        auth.logout(user);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString()).build();
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value).httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/").maxAge(maxAge).build();
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
