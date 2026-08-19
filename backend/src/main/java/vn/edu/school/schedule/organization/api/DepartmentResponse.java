package vn.edu.school.schedule.organization.api;

import java.util.UUID;

public record DepartmentResponse(UUID id, String name, String description, boolean isActive, long version) { }
