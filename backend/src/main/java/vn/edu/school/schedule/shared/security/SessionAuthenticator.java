package vn.edu.school.schedule.shared.security;

public interface SessionAuthenticator {
    AuthenticatedUser authenticate(String rawSession);
    boolean validCsrf(AuthenticatedUser user, String rawToken);
}
