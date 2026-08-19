package vn.edu.school.schedule.weeklyplan.api;

import java.util.UUID;

public record PlanTargetResponse(String targetType, UUID targetId, String label) { }
