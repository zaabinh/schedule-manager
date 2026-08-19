package vn.edu.school.schedule.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final SessionAuthenticator auth;

    public SessionCsrfFilter(SessionAuthenticator auth) { this.auth = auth; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE.contains(request.getMethod())) {
            Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
                    : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof AuthenticatedUser user
                    && !auth.validCsrf(user, request.getHeader(SecurityHeaders.CSRF))) {
                SecurityJson.write(response, 403, "CSRF_INVALID", "CSRF token không hợp lệ.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
