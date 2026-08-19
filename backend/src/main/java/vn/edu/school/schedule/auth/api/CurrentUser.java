package vn.edu.school.schedule.auth.api;

import java.util.List;
import java.util.UUID;

public record CurrentUser(
        UUID id,
        String email,
        String displayName,
        String systemRole,
        String status,
        ResourceRef department,
        List<ResourceRef> businessRoles,
        ResourceRef homeroomClass,
        long version) { }
