package vn.edu.school.schedule.weeklyplan.api;

import java.util.List;

public record CopyPlanResponse(WeeklyPlanResponse plan, List<String> warnings) { }
