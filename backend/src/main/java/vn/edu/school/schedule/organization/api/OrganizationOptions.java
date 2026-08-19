package vn.edu.school.schedule.organization.api;

import java.util.List;
import vn.edu.school.schedule.auth.api.ResourceRef;

public record OrganizationOptions(List<ResourceRef> academicYears, List<ResourceRef> availableTeachers) { }
