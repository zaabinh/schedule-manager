package vn.edu.school.schedule.dashboard.api;

import java.util.List;
import vn.edu.school.schedule.weeklyplan.api.PlanDayResponse;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanResponse;

public record UserDashboardResponse(WeeklyPlanResponse currentWeek, List<RelevantItem> relevantToMe,
                                    PlanDayResponse today, WeeklyPlanResponse weeklyPlan,
                                    NotificationSummary notificationSummary, TaskSummary taskSummary) {
    public record NotificationSummary(long unreadCount) { }
    public record TaskSummary(long total, long completed, long incomplete, long overdue) { }
}
