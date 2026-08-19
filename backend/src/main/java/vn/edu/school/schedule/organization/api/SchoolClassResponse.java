package vn.edu.school.schedule.organization.api;

import java.util.UUID;
import vn.edu.school.schedule.auth.api.ResourceRef;

public record SchoolClassResponse(UUID id, ResourceRef academicYear, String name, short grade,
                                  ResourceRef homeroomTeacher, boolean isActive, long version) { }
