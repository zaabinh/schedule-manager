package vn.edu.school.schedule.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;

    public AuthOriginFilter(@Value("${app.security.allowed-origins}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            String referer = request.getHeader("Referer");
            if (referer != null) {
                try {
                    URI uri = URI.create(referer);
                    if (uri.getScheme() != null && uri.getAuthority() != null) {
                        origin = uri.getScheme() + "://" + uri.getAuthority();
                    }
                } catch (IllegalArgumentException ignored) {
                    // A malformed Referer is treated exactly like a disallowed origin.
                }
            }
        }
        if (origin == null || !allowedOrigins.contains(origin)) {
            SecurityJson.write(response, 403, "ORIGIN_INVALID", "Nguồn yêu cầu không được phép.");
            return;
        }
        chain.doFilter(request, response);
    }
}
