package vn.edu.school.schedule.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.school.schedule.dashboard.api.AdminDashboardResponse;
import vn.edu.school.schedule.dashboard.api.RelevantItem;
import vn.edu.school.schedule.dashboard.api.UserDashboardResponse;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.weeklyplan.WeeklyPlanService;
import vn.edu.school.schedule.weeklyplan.api.PlanDayResponse;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanResponse;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final WeeklyPlanService plans;
    public DashboardService(JdbcTemplate jdbc, WeeklyPlanService plans) { this.jdbc = jdbc; this.plans = plans; }

    public UserDashboardResponse user(UUID weekId, AuthenticatedUser actor) {
        Long unread = jdbc.queryForObject("SELECT count(*) FROM notification_recipients WHERE user_id=? AND read_at IS NULL", Long.class, actor.id());
        UserDashboardResponse.TaskSummary taskSummary = jdbc.queryForObject("""
                SELECT count(*),
                       count(*) FILTER (WHERE status='COMPLETED'),
                       count(*) FILTER (WHERE status='TODO'),
                       count(*) FILTER (WHERE status='TODO' AND due_at < now())
                FROM tasks WHERE assignee_user_id=?
                """, (rs, rowNum) -> new UserDashboardResponse.TaskSummary(
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), actor.id());
        WeeklyPlanResponse plan;
        try {
            plan = weekId == null ? plans.currentPublished() : plans.getByWeek(weekId, actor);
        } catch (ApiException exception) {
            if (weekId != null || !"PLAN_NOT_FOUND".equals(exception.code())) throw exception;
            return new UserDashboardResponse(null, List.of(), null, null,
                    new UserDashboardResponse.NotificationSummary(unread == null ? 0 : unread), taskSummary);
        }
        Map<String, RelevantBuilder> relevant = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.id,s.section_type,s.content,t.target_type
                FROM plan_sections s JOIN plan_section_targets t ON t.plan_section_id=s.id
                JOIN users u ON u.id=?
                WHERE s.weekly_plan_id=? AND (
                  t.target_type='ALL' OR t.user_id=u.id OR t.department_id=u.department_id OR
                  EXISTS (SELECT 1 FROM user_roles ur JOIN business_roles r ON r.id=ur.business_role_id
                          WHERE ur.user_id=u.id AND r.is_active=true AND r.id=t.business_role_id))
                ORDER BY s.display_order
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            UUID id = rs.getObject(1, UUID.class);
            String sectionType = rs.getString(2);
            String content = rs.getString(3);
            String matchedBy = rs.getString(4);
            String key = "SECTION:" + id;
            RelevantBuilder item = relevant.computeIfAbsent(key, ignored -> new RelevantBuilder("SECTION", id,
                    sectionTitle(sectionType), content, "/weekly-plan"));
            item.matches.add(matchedBy);
        }, actor.id(), plan.id());
        jdbc.query("""
                SELECT t.id,t.title,t.description FROM tasks t
                WHERE t.weekly_plan_id=? AND t.assignee_user_id=? ORDER BY t.due_at,t.id
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> relevant.putIfAbsent("TASK:" + rs.getObject(1, UUID.class),
                new RelevantBuilder("TASK", rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), "/assignments", "TASK")),
                plan.id(), actor.id());
        jdbc.query("""
                SELECT c.id,c.name,CASE WHEN p.morning_duty_class_id=c.id THEN 'MORNING' ELSE 'AFTERNOON' END
                FROM weekly_plans p JOIN school_classes c
                  ON c.id=p.morning_duty_class_id OR c.id=p.afternoon_duty_class_id
                WHERE p.id=? AND c.is_active=true AND c.homeroom_teacher_id=?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            UUID id = rs.getObject(1, UUID.class);
            relevant.putIfAbsent("HOMEROOM_CLASS:" + id, new RelevantBuilder("HOMEROOM_CLASS", id,
                    "Lớp " + rs.getString(2) + " trực " + ("MORNING".equals(rs.getString(3)) ? "buổi sáng" : "buổi chiều"),
                    "Lịch trực lớp chủ nhiệm trong tuần này.", "/weekly-plan", "HOMEROOM_CLASS"));
        }, plan.id(), actor.id());
        PlanDayResponse today = plan.days().stream().filter(day -> day.date().equals(LocalDate.now())).findFirst().orElse(null);
        List<RelevantItem> items = relevant.values().stream().map(RelevantBuilder::build).toList();
        return new UserDashboardResponse(plan, items, today, plan,
                new UserDashboardResponse.NotificationSummary(unread == null ? 0 : unread), taskSummary);
    }

    public AdminDashboardResponse admin() {
        WeeklyPlanResponse current;
        try { current = plans.currentForAdmin(); } catch (RuntimeException ignored) { current = null; }
        Map<String, Long> attention = new LinkedHashMap<>();
        attention.put("pendingUsers", count("SELECT count(*) FROM users WHERE status='PENDING'"));
        attention.put("openConversations", count("SELECT count(*) FROM conversations WHERE status='OPEN'"));
        attention.put("incompleteTasks", count("SELECT count(*) FROM tasks WHERE status='TODO'"));
        attention.put("unpublishedPlans", count("SELECT count(*) FROM weekly_plans WHERE status='DRAFT'"));
        return new AdminDashboardResponse(current, attention);
    }

    private long count(String sql) { Long value = jdbc.queryForObject(sql, Long.class); return value == null ? 0 : value; }
    private String sectionTitle(String type) { return switch (type) {
        case "ACADEMIC_AFFAIRS" -> "Công tác chuyên môn";
        case "FACILITIES_OFFICE" -> "Cơ sở vật chất – thiết bị – văn phòng";
        case "YOUTH_UNION" -> "Đoàn Thanh niên – Hội LHTN";
        case "HOMEROOM_TEACHERS" -> "Giáo viên chủ nhiệm";
        default -> "Giáo viên";
    }; }
    private static final class RelevantBuilder {
        final String kind; final UUID id; final String title; final String content; final String link;
        final LinkedHashSet<String> matches = new LinkedHashSet<>();
        RelevantBuilder(String kind, UUID id, String title, String content, String link, String... matches) {
            this.kind=kind; this.id=id; this.title=title; this.content=content; this.link=link;
            this.matches.addAll(List.of(matches));
        }
        RelevantItem build() { return new RelevantItem(kind,id,title,content == null ? "" : content,List.copyOf(matches),link); }
    }
}
