package vn.edu.school.schedule.reminder;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SaturdayPlanReminderScheduler {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper json;
    private final ZoneId schoolZone;

    public SaturdayPlanReminderScheduler(JdbcTemplate jdbc, Clock clock, ObjectMapper json,
                                         @Value("${app.school-zone}") String schoolZone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
        this.schoolZone = ZoneId.of(schoolZone);
    }

    @Scheduled(cron = "0 0 8,17 * * SAT", zone = "${app.school-zone}")
    @Transactional
    public void run() {
        ZonedDateTime now = clock.instant().atZone(schoolZone);
        String jobKey = "SATURDAY_PLAN:" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH"));
        int inserted = jdbc.update("""
                INSERT INTO scheduler_job_runs(job_key,started_at,result) VALUES (?,now(),'RUNNING')
                ON CONFLICT DO NOTHING
                """, jobKey);
        if (inserted == 0) return;
        List<Week> weeks = jdbc.query("""
                SELECT w.id,w.display_number FROM school_weeks w
                WHERE w.start_date>? ORDER BY w.start_date LIMIT 1
                """, (rs, row) -> new Week(rs.getObject(1, UUID.class), rs.getInt(2)), now.toLocalDate());
        if (weeks.isEmpty() || published(weeks.getFirst().id())) {
            jdbc.update("UPDATE scheduler_job_runs SET completed_at=now(),result='SKIPPED' WHERE job_key=?", jobKey);
            return;
        }
        List<UUID> admins = jdbc.query("SELECT id FROM users WHERE system_role='ADMIN' AND status='ACTIVE'",
                (rs, row) -> rs.getObject(1, UUID.class));
        if (admins.isEmpty()) {
            jdbc.update("UPDATE scheduler_job_runs SET completed_at=now(),result='FAILED' WHERE job_key=?", jobKey);
            return;
        }
        Week week = weeks.getFirst();
        Map<String, Object> payload = Map.of("notifyWebsite", true, "notifyEmail", true,
                "adminUserId", admins.getFirst(), "weekNumber", week.number());
        jdbc.update("""
                INSERT INTO outbox_messages(id,event_type,aggregate_type,aggregate_id,deduplication_key,payload)
                VALUES (?,'SATURDAY_PLAN_REMINDER','SchoolWeek',?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), week.id(), jobKey, json(payload));
        jdbc.update("UPDATE scheduler_job_runs SET completed_at=now(),result='SUCCEEDED' WHERE job_key=?", jobKey);
    }

    private boolean published(UUID weekId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM weekly_plans WHERE school_week_id=? AND status='PUBLISHED'", Long.class, weekId);
        return count != null && count > 0;
    }

    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException failure) { throw new IllegalStateException(failure); }
    }

    private record Week(UUID id, int number) { }
}
