package vn.edu.school.schedule.user.api;

import java.util.List;
import vn.edu.school.schedule.auth.api.ResourceRef;

public record ApprovalOptions(List<ResourceRef> departments, List<ResourceRef> businessRoles, List<ResourceRef> classes) { }
