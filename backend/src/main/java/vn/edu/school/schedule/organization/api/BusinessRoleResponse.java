package vn.edu.school.schedule.organization.api;

import java.util.UUID;

public record BusinessRoleResponse(UUID id, String name, String description, boolean isProtected,
                                   boolean isActive, long version) { }
