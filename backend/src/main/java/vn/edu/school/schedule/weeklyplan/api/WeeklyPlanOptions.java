package vn.edu.school.schedule.weeklyplan.api;

import java.util.List;
import vn.edu.school.schedule.auth.api.ResourceRef;

public record WeeklyPlanOptions(List<ResourceRef> dutyClasses, List<ResourceRef> departments,
                                List<ResourceRef> businessRoles, List<ResourceRef> users) { }
