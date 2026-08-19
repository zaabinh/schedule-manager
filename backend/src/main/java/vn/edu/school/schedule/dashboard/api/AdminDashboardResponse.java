package vn.edu.school.schedule.dashboard.api;

import java.util.Map;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanResponse;

public record AdminDashboardResponse(WeeklyPlanResponse currentPlan, Map<String, Long> needsAttention) { }
