package vn.edu.school.schedule.shared.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String systemRole, String sessionHash, String csrfHash) { }
