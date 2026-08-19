package vn.edu.school.schedule.dashboard.api;

import java.util.List;
import vn.edu.school.schedule.weeklyplan.api.PlanDayResponse;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanResponse;

public record UserDashboardResponse(WeeklyPlanResponse currentWeek, List<RelevantItem> relevantToMe,
                                    PlanDayResponse today, WeeklyPlanResponse weeklyPlan,
                                    NotificationSummary notificationSummary) {
    public record NotificationSummary(long unreadCount) { }
}
