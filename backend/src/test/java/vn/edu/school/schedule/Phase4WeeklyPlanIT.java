package vn.edu.school.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.notification.OutboxWorker;
import vn.edu.school.schedule.reminder.ReminderWorker;
import vn.edu.school.schedule.reminder.SaturdayPlanReminderScheduler;

@SpringBootTest
@Import(Phase4WeeklyPlanIT.FixedClockConfig.class)
class Phase4WeeklyPlanIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final String ORIGIN = "http://localhost:3000";
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000503");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000510");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000504");
    private static final UUID YEAR_ID = UUID.fromString("00000000-0000-0000-0000-000000000505");
    private static final UUID CLASS_ID = UUID.fromString("00000000-0000-0000-0000-000000000506");
    private static final UUID SOURCE_WEEK = UUID.fromString("00000000-0000-0000-0000-000000000507");
    private static final UUID BLANK_WEEK = UUID.fromString("00000000-0000-0000-0000-000000000508");
    private static final UUID TARGET_WEEK = UUID.fromString("00000000-0000-0000-0000-000000000509");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.session-pepper", () -> "phase-4-integration-pepper-at-least-32-characters");
        registry.add("app.outbox.initial-delay-ms", () -> "3600000");
        registry.add("app.reminder.initial-delay-ms", () -> "3600000");
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired OutboxWorker outbox;
    @Autowired ReminderWorker reminderWorker;
    @Autowired SaturdayPlanReminderScheduler saturdayScheduler;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE TABLE audit_logs");
        jdbc.update("DELETE FROM outbox_messages");
        jdbc.update("DELETE FROM notification_recipients");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM api_idempotency_keys");
        jdbc.update("DELETE FROM scheduler_job_runs");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM task_attachments");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM reminders");
        jdbc.update("DELETE FROM conversation_messages");
        jdbc.update("DELETE FROM conversations");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM plan_section_targets");
        jdbc.update("DELETE FROM plan_sections");
        jdbc.update("DELETE FROM day_sessions");
        jdbc.update("DELETE FROM weekly_plans");
        jdbc.update("DELETE FROM school_weeks");
        jdbc.update("DELETE FROM school_classes");
        jdbc.update("DELETE FROM academic_years");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM departments");
        jdbc.update("DELETE FROM business_roles WHERE is_protected=false");
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)", DEPARTMENT_ID, "Văn phòng", "van phong");
        jdbc.update("INSERT INTO business_roles(id,name,normalized_name) VALUES (?,?,?)", ROLE_ID, "Tổ trưởng", "to truong");
        insertUser(ADMIN_ID, "admin@example.edu.vn", "ADMIN", "Hiệu trưởng", "Admin@2026");
        insertUser(USER_ID, "teacher@example.edu.vn", "USER", "Nguyễn Văn An", "Teacher@2026");
        jdbc.update("INSERT INTO academic_years(id,name,start_date,created_by) VALUES (?,?,?,?)",
                YEAR_ID, "2026-2027", LocalDate.of(2026, 8, 17), ADMIN_ID);
        jdbc.update("INSERT INTO school_classes(id,academic_year_id,name,normalized_name,grade) VALUES (?,?,?,?,?)",
                CLASS_ID, YEAR_ID, "11A2", "11a2", 11);
        insertWeek(SOURCE_WEEK, 1, 1, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));
        insertWeek(BLANK_WEEK, 2, 2, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));
        insertWeek(TARGET_WEEK, 3, 3, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 25));
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2026-08-19T07:00:00Z"), ZoneOffset.UTC); }
    }

    @Test
    void weeklyPlanCreateUpdateCopyVisibilitySecurityAndAudit() throws Exception {
        mvc.perform(get("/api/v1/weeks/{id}/plan", SOURCE_WEEK)).andExpect(status().isUnauthorized());
        var login = mvc.perform(login("admin@example.edu.vn", "Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("session");
        String csrf = login.getResponse().getHeader("X-CSRF-Token");

        mvc.perform(post("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(cookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(cookie).header("X-CSRF-Token", csrf))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.sections.length()").value(5))
                .andExpect(jsonPath("$.data.days.length()").value(7))
                .andExpect(jsonPath("$.data.days[0].sessions.length()").value(2));
        mvc.perform(post("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(cookie).header("X-CSRF-Token", csrf))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PLAN_EXISTS"));
        UUID sourcePlan = jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?", UUID.class, SOURCE_WEEK);
        mvc.perform(get("/api/v1/weekly-plans/{id}/options", sourcePlan).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dutyClasses[0].name").value("11A2"))
                .andExpect(jsonPath("$.data.businessRoles[?(@.id == '" + ROLE_ID + "')]").exists());

        String update = updateRequest(0, false);
        mvc.perform(patch("/api/v1/weekly-plans/{id}", sourcePlan).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.morningDutyClass.name").value("11A2"))
                .andExpect(jsonPath("$.data.sections[0].content").value("Nội dung mục 1"))
                .andExpect(jsonPath("$.data.sections[0].targets[0].label").value("Tổ trưởng"))
                .andExpect(jsonPath("$.data.days[0].sessions[0].baseContent").value("Nền 0"));
        mvc.perform(patch("/api/v1/weekly-plans/{id}", sourcePlan).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(update))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
        mvc.perform(patch("/api/v1/weekly-plans/{id}", sourcePlan).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(updateRequest(1, true)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("PLAN_STRUCTURE_INVALID"));

        jdbc.update("""
                INSERT INTO events(id,weekly_plan_id,content,start_date,created_by) VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), sourcePlan, "Sự kiện không copy", LocalDate.of(2026, 9, 8), ADMIN_ID);
        jdbc.update("""
                INSERT INTO tasks(id,weekly_plan_id,assignee_user_id,title,due_at,created_by)
                VALUES (?,?,?,?,now()+interval '1 day',?)
                """, UUID.randomUUID(), sourcePlan, USER_ID, "Task không copy", ADMIN_ID);
        jdbc.update("UPDATE business_roles SET is_active=false WHERE id=?", ROLE_ID);

        String copyBody = "{\"sourceWeekId\":\"" + SOURCE_WEEK + "\"}";
        mvc.perform(post("/api/v1/weeks/{id}/plan/copy", TARGET_WEEK).cookie(cookie)
                        .header("X-CSRF-Token", csrf).contentType("application/json").content(copyBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/api/v1/weeks/{id}/plan/copy", TARGET_WEEK).cookie(cookie)
                        .header("X-CSRF-Token", csrf).header("Idempotency-Key", "copy-week-3")
                        .contentType("application/json").content(copyBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.plan.days.length()").value(5))
                .andExpect(jsonPath("$.data.plan.sections[0].content").value("Nội dung mục 1"))
                .andExpect(jsonPath("$.data.plan.morningDutyClass").doesNotExist())
                .andExpect(jsonPath("$.data.warnings[0]").value("DAY_COUNT_MISMATCH"))
                .andExpect(jsonPath("$.data.warnings[1]").value("INACTIVE_TARGET_COPIED"));
        mvc.perform(post("/api/v1/weeks/{id}/plan/copy", TARGET_WEEK).cookie(cookie)
                        .header("X-CSRF-Token", csrf).header("Idempotency-Key", "copy-week-3")
                        .contentType("application/json").content(copyBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.plan.id").isNotEmpty())
                .andExpect(jsonPath("$.data.warnings[0]").value("DAY_COUNT_MISMATCH"));
        UUID targetPlan = jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?", UUID.class, TARGET_WEEK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM weekly_plans WHERE school_week_id=?", Integer.class, TARGET_WEEK)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE weekly_plan_id=?", Integer.class, targetPlan)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE weekly_plan_id=?", Integer.class, targetPlan)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM day_sessions WHERE weekly_plan_id=?", Integer.class, targetPlan)).isEqualTo(10);

        var userLogin = mvc.perform(login("teacher@example.edu.vn", "Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie userCookie = userLogin.getResponse().getCookie("session");
        String userCsrf = userLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(get("/api/v1/weeks/{id}/plan", TARGET_WEEK).cookie(userCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PLAN_NOT_FOUND"));
        mvc.perform(post("/api/v1/weeks/{id}/plan", BLANK_WEEK).cookie(userCookie)
                        .header("X-CSRF-Token", userCsrf))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE weekly_plans SET status='PUBLISHED',published_at=now(),published_by=? WHERE id=?", ADMIN_ID, sourcePlan);
        mvc.perform(get("/api/v1/weekly-plans/published"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/weekly-plans/published").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(SOURCE_WEEK.toString()))
                .andExpect(jsonPath("$.data[0].planStatus").value("PUBLISHED"));
        mvc.perform(get("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(userCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='WeeklyPlan'", Integer.class);
        assertThat(audits).isGreaterThanOrEqualTo(3);
    }

    @Test
    void phase5EventValidationPublishAndPublishedMutation() throws Exception {
        var adminLogin = mvc.perform(login("admin@example.edu.vn", "Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie admin = adminLogin.getResponse().getCookie("session");
        String csrf = adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(post("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(admin).header("X-CSRF-Token", csrf))
                .andExpect(status().isCreated());
        UUID planId = jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?", UUID.class, SOURCE_WEEK);
        mvc.perform(patch("/api/v1/weekly-plans/{id}", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(updateRequest(0, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));

        var userLogin = mvc.perform(login("teacher@example.edu.vn", "Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie user = userLogin.getResponse().getCookie("session");
        mvc.perform(get("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(user)).andExpect(status().isNotFound());

        String invalidEvent = "{\"content\":\"Sai giờ\",\"startTime\":\"15:00\",\"endTime\":\"14:00\"}";
        mvc.perform(post("/api/v1/weekly-plans/{id}/events", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(invalidEvent))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("EVENT_TIME_INVALID"));
        String eventBody = """
                {"content":"Hội nghị hai ngày","startDate":"2026-09-08","endDate":"2026-09-09",
                 "session":"MORNING","startTime":"08:00","endTime":"10:00","location":"Hội trường"}
                """;
        var created = mvc.perform(post("/api/v1/weekly-plans/{id}/events", planId).cookie(admin)
                        .header("X-CSRF-Token", csrf).contentType("application/json").content(eventBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(0)).andReturn();
        String eventId = json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
        mvc.perform(get("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[1].sessions[0].events[0].id").value(eventId))
                .andExpect(jsonPath("$.data.days[2].sessions[0].events[0].id").value(eventId));
        mvc.perform(get("/api/v1/weekly-plans/{id}/validation", planId).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.warnings[?(@.code == 'MISSING_AFTERNOON_DUTY')]").exists());

        String publish = "{\"version\":2,\"publishWithWarnings\":false}";
        mvc.perform(post("/api/v1/weekly-plans/{id}/publish", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(publish))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/api/v1/weekly-plans/{id}/publish", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .header("Idempotency-Key", "publish-source").contentType("application/json").content(publish))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("PUBLISH_WARNINGS_UNCONFIRMED"));
        String confirmed = "{\"version\":2,\"publishWithWarnings\":true}";
        mvc.perform(post("/api/v1/weekly-plans/{id}/publish", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .header("Idempotency-Key", "publish-source").contentType("application/json").content(confirmed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty()).andExpect(jsonPath("$.data.publishedBy").value(ADMIN_ID.toString()));
        mvc.perform(post("/api/v1/weekly-plans/{id}/publish", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .header("Idempotency-Key", "publish-source").contentType("application/json").content(confirmed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(user)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/weekly-plans/current").cookie(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId.toString()));

        mvc.perform(post("/api/v1/weekly-plans/{id}/events", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content("{\"content\":\"Sau công bố\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("NOTIFICATION_CHOICE_REQUIRED"));
        mvc.perform(delete("/api/v1/events/{id}", eventId).queryParam("version", "0")
                        .queryParam("notifyWebsite", "false").queryParam("notifyEmail", "false")
                        .cookie(admin).header("X-CSRF-Token", csrf))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE aggregate_id=?", Integer.class, planId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='Event'", Integer.class)).isEqualTo(2);
    }

    @Test
    void phase6RelevantProjectionDeduplicatesMatchesAndProtectsDashboard() throws Exception {
        var adminLogin = mvc.perform(login("admin@example.edu.vn", "Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie admin = adminLogin.getResponse().getCookie("session");
        String csrf = adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(post("/api/v1/weeks/{id}/plan", SOURCE_WEEK).cookie(admin).header("X-CSRF-Token", csrf))
                .andExpect(status().isCreated());
        UUID planId = jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?", UUID.class, SOURCE_WEEK);
        mvc.perform(patch("/api/v1/weekly-plans/{id}", planId).cookie(admin).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(updateRequest(0, false))).andExpect(status().isOk());
        jdbc.update("INSERT INTO user_roles(user_id,business_role_id) VALUES (?,?)", USER_ID, ROLE_ID);
        jdbc.update("UPDATE school_classes SET homeroom_teacher_id=? WHERE id=?", USER_ID, CLASS_ID);
        UUID firstSection = jdbc.queryForObject("SELECT id FROM plan_sections WHERE weekly_plan_id=? AND display_order=1", UUID.class, planId);
        jdbc.update("""
                INSERT INTO plan_section_targets(id,plan_section_id,target_type,department_id) VALUES (?,?,'DEPARTMENT',?)
                """, UUID.randomUUID(), firstSection, DEPARTMENT_ID);
        jdbc.update("UPDATE weekly_plans SET status='PUBLISHED',published_at=now(),published_by=? WHERE id=?", ADMIN_ID, planId);

        var userLogin = mvc.perform(login("teacher@example.edu.vn", "Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie user = userLogin.getResponse().getCookie("session");
        mvc.perform(get("/api/v1/dashboard/me").cookie(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relevantToMe.length()").value(2))
                .andExpect(jsonPath("$.data.relevantToMe[?(@.kind == 'SECTION')].matchedBy.length()").value(2))
                .andExpect(jsonPath("$.data.relevantToMe[?(@.kind == 'HOMEROOM_CLASS')]").exists())
                .andExpect(jsonPath("$.data.weeklyPlan.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.taskSummary.total").value(0));
        mvc.perform(get("/api/v1/dashboard/admin").cookie(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dashboard/me").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dashboard/admin").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPlan.id").value(planId.toString()));
    }

    @Test
    void phase7TaskOwnershipOverdueCompletionSummaryAndHooks() throws Exception {
        var adminLogin=mvc.perform(login("admin@example.edu.vn","Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie admin=adminLogin.getResponse().getCookie("session");String adminCsrf=adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(post("/api/v1/weeks/{id}/plan",SOURCE_WEEK).cookie(admin).header("X-CSRF-Token",adminCsrf)).andExpect(status().isCreated());
        UUID planId=jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?",UUID.class,SOURCE_WEEK);
        String body="{\"weeklyPlanId\":\""+planId+"\",\"assigneeUserId\":\""+USER_ID+"\",\"title\":\"Nộp báo cáo\",\"dueAt\":\"2020-01-01T00:00:00Z\"}";
        var created=mvc.perform(post("/api/v1/tasks").cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.displayStatus").value("OVERDUE")).andExpect(jsonPath("$.data.version").value(0)).andReturn();
        String taskId=json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
        mvc.perform(get("/api/v1/tasks/summary").cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.overdue").value(1));
        var userLogin=mvc.perform(login("teacher@example.edu.vn","Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie user=userLogin.getResponse().getCookie("session");String userCsrf=userLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(get("/api/v1/dashboard/me").cookie(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyPlan").doesNotExist())
                .andExpect(jsonPath("$.data.today").doesNotExist())
                .andExpect(jsonPath("$.data.taskSummary.total").value(1))
                .andExpect(jsonPath("$.data.taskSummary.incomplete").value(1))
                .andExpect(jsonPath("$.data.taskSummary.overdue").value(1));
        mvc.perform(get("/api/v1/tasks/me").cookie(user)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(taskId));
        mvc.perform(patch("/api/v1/tasks/{id}/complete",taskId).cookie(user).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/tasks/{id}/complete",taskId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED")).andExpect(jsonPath("$.data.completedAt").isNotEmpty());
        mvc.perform(patch("/api/v1/tasks/{id}/complete",taskId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/tasks").cookie(user)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE aggregate_type='Task'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='Task'",Integer.class)).isEqualTo(2);
    }

    @Test
    void phase8NotificationFanoutReadIsolationAndOutboxIdempotency() throws Exception {
        var adminLogin=mvc.perform(login("admin@example.edu.vn","Admin@2026")).andExpect(status().isOk()).andReturn();Cookie admin=adminLogin.getResponse().getCookie("session");String adminCsrf=adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(post("/api/v1/weeks/{id}/plan",SOURCE_WEEK).cookie(admin).header("X-CSRF-Token",adminCsrf)).andExpect(status().isCreated());UUID planId=jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?",UUID.class,SOURCE_WEEK);
        String task="{\"weeklyPlanId\":\""+planId+"\",\"assigneeUserId\":\""+USER_ID+"\",\"title\":\"Thông báo task\",\"dueAt\":\"2030-01-01T00:00:00Z\"}";
        mvc.perform(post("/api/v1/tasks").cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content(task)).andExpect(status().isCreated());
        assertThat(outbox.drain()).isEqualTo(1);assertThat(outbox.drain()).isZero();
        var userLogin=mvc.perform(login("teacher@example.edu.vn","Teacher@2026")).andExpect(status().isOk()).andReturn();Cookie user=userLogin.getResponse().getCookie("session");String userCsrf=userLogin.getResponse().getHeader("X-CSRF-Token");
        var listed=mvc.perform(get("/api/v1/notifications").cookie(user)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].readAt").doesNotExist()).andReturn();String notificationId=json.readTree(listed.getResponse().getContentAsString()).get("data").get(0).get("id").asText();
        mvc.perform(get("/api/v1/notifications/unread-count").cookie(user)).andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(patch("/api/v1/notifications/{id}/read",notificationId).cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content("{}" )).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notifications/{id}/read",notificationId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.readAt").isNotEmpty());
        mvc.perform(patch("/api/v1/notifications/read-all").cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{}" )).andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_messages WHERE aggregate_type='Task'",String.class)).isEqualTo("PROCESSED");

        UUID poisonId=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_messages(id,event_type,aggregate_type,aggregate_id,deduplication_key,payload)
                VALUES (?,'TASK_UPDATED','Task',?,'phase8-poison','{}'::jsonb)
                """,UUID.randomUUID(),poisonId);
        for(int attempt=1;attempt<=3;attempt++){
            assertThat(outbox.drain()).isEqualTo(1);
            String expected=attempt==3?"FAILED":"PENDING";
            assertThat(jdbc.queryForObject("SELECT status FROM outbox_messages WHERE deduplication_key='phase8-poison'",String.class)).isEqualTo(expected);
            if(attempt<3)jdbc.update("UPDATE outbox_messages SET available_at=now() WHERE deduplication_key='phase8-poison'");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks WHERE title='Thông báo task'",Integer.class)).isEqualTo(1);
    }

    @Test
    void phase9ReminderPresetOwnershipDeliveryAndSaturdayIdempotency() throws Exception {
        var adminLogin=mvc.perform(login("admin@example.edu.vn","Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie admin=adminLogin.getResponse().getCookie("session");String adminCsrf=adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(post("/api/v1/weeks/{id}/plan",SOURCE_WEEK).cookie(admin).header("X-CSRF-Token",adminCsrf)).andExpect(status().isCreated());
        UUID planId=jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?",UUID.class,SOURCE_WEEK);
        String eventBody="{\"content\":\"Họp chuyên môn\",\"startDate\":\"2026-09-08\",\"startTime\":\"09:00\"}";
        var created=mvc.perform(post("/api/v1/weekly-plans/{id}/events",planId).cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content(eventBody)).andExpect(status().isCreated()).andReturn();
        String eventId=json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();
        jdbc.update("UPDATE weekly_plans SET status='PUBLISHED',published_at=now(),published_by=? WHERE id=?",ADMIN_ID,planId);

        var userLogin=mvc.perform(login("teacher@example.edu.vn","Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie user=userLogin.getResponse().getCookie("session");String userCsrf=userLogin.getResponse().getHeader("X-CSRF-Token");
        var personal=mvc.perform(post("/api/v1/events/{id}/reminders",eventId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"preset\":\"MINUTES_30\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data[0].source").value("USER")).andReturn();
        String personalId=json.readTree(personal.getResponse().getContentAsString()).get("data").get(0).get("id").asText();
        mvc.perform(post("/api/v1/events/{id}/reminders",eventId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"preset\":\"CUSTOM\",\"remindAt\":\"2026-09-08T01:30:00Z\"}"))
                .andExpect(status().isCreated());
        var general=mvc.perform(post("/api/v1/events/{id}/reminders",eventId).cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content("{\"preset\":\"HOUR_1\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].source").value("ADMIN")).andReturn();
        String adminReminderId=json.readTree(general.getResponse().getContentAsString()).get("data").get(0).get("id").asText();
        mvc.perform(get("/api/v1/reminders/me").cookie(user)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(delete("/api/v1/reminders/{id}",adminReminderId).cookie(user).header("X-CSRF-Token",userCsrf)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/reminders/{id}",personalId).cookie(user).header("X-CSRF-Token",userCsrf)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM reminders WHERE id=?",String.class,UUID.fromString(personalId))).isEqualTo("CANCELLED");

        UUID dueId=jdbc.queryForObject("SELECT id FROM reminders WHERE source='USER' AND status='PENDING'",UUID.class);
        jdbc.update("UPDATE reminders SET remind_at=now()-interval '1 minute' WHERE id=?",dueId);
        assertThat(reminderWorker.drain()).isEqualTo(1);assertThat(reminderWorker.drain()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM reminders WHERE id=?",String.class,dueId)).isEqualTo("SENT");

        saturdayScheduler.run();saturdayScheduler.run();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduler_job_runs",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT result FROM scheduler_job_runs",String.class)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE event_type='SATURDAY_PLAN_REMINDER'",Integer.class)).isZero();
        jdbc.update("DELETE FROM scheduler_job_runs");
        jdbc.update("UPDATE weekly_plans SET status='DRAFT',published_at=NULL,published_by=NULL WHERE id=?",planId);
        saturdayScheduler.run();saturdayScheduler.run();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduler_job_runs",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE event_type='SATURDAY_PLAN_REMINDER'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='Reminder'",Integer.class)).isEqualTo(4);
    }

    @Test
    void phase10ConversationOwnershipMessagesCloseAndNotifications() throws Exception {
        insertUser(OTHER_USER_ID,"other@example.edu.vn","USER","Giáo viên khác","Other@2026");
        var userLogin=mvc.perform(login("teacher@example.edu.vn","Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie user=userLogin.getResponse().getCookie("session");String userCsrf=userLogin.getResponse().getHeader("X-CSRF-Token");
        var created=mvc.perform(post("/api/v1/conversations").cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json")
                        .content("{\"subject\":\"Đề nghị hỗ trợ\",\"category\":\"Chuyên môn\",\"message\":\"Nội dung trao đổi đầu tiên\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.messages.length()").value(1)).andReturn();
        String conversationId=json.readTree(created.getResponse().getContentAsString()).get("data").get("id").asText();

        var otherLogin=mvc.perform(login("other@example.edu.vn","Other@2026")).andExpect(status().isOk()).andReturn();
        Cookie other=otherLogin.getResponse().getCookie("session");String otherCsrf=otherLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(get("/api/v1/conversations/{id}",conversationId).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/conversations/{id}/messages",conversationId).cookie(other).header("X-CSRF-Token",otherCsrf).contentType("application/json").content("{\"content\":\"Không được phép\"}"))
                .andExpect(status().isNotFound());

        var adminLogin=mvc.perform(login("admin@example.edu.vn","Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie admin=adminLogin.getResponse().getCookie("session");String adminCsrf=adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(get("/api/v1/conversations").cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post("/api/v1/conversations/{id}/messages",conversationId).cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content("{\"content\":\"Phản hồi từ Hiệu trưởng\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/v1/conversations/{id}/messages",conversationId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"content\":\"Xin cảm ơn\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.messages.length()").value(3)).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(patch("/api/v1/conversations/{id}/close",conversationId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"version\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/conversations/{id}/close",conversationId).cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED")).andExpect(jsonPath("$.data.version").value(3));
        mvc.perform(post("/api/v1/conversations/{id}/messages",conversationId).cookie(user).header("X-CSRF-Token",userCsrf).contentType("application/json").content("{\"content\":\"Tin sau đóng\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONVERSATION_CLOSED"));
        mvc.perform(patch("/api/v1/conversations/{id}/close",conversationId).cookie(admin).header("X-CSRF-Token",adminCsrf).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));

        assertThat(outbox.drain()).isEqualTo(4);assertThat(outbox.drain()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE entity_type='Conversation'",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='Conversation'",Integer.class)).isEqualTo(4);
    }

    @Test
    void phase11AuditRedactionAppendOnlyAndExcelUnicodeFormulaProtection() throws Exception {
        var adminLogin=mvc.perform(login("admin@example.edu.vn","Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie admin=adminLogin.getResponse().getCookie("session");String adminCsrf=adminLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(post("/api/v1/weeks/{id}/plan",SOURCE_WEEK).cookie(admin).header("X-CSRF-Token",adminCsrf)).andExpect(status().isCreated());
        UUID planId=jdbc.queryForObject("SELECT id FROM weekly_plans WHERE school_week_id=?",UUID.class,SOURCE_WEEK);
        jdbc.update("UPDATE plan_sections SET content='=HYPERLINK(\"https://invalid\",\"Bấm\") — Tiếng Việt' WHERE weekly_plan_id=? AND display_order=1",planId);
        var export=mvc.perform(get("/api/v1/weekly-plans/{id}/export",planId).cookie(admin)).andExpect(status().isOk()).andReturn();
        assertThat(export.getResponse().getContentType()).contains("spreadsheetml");
        List<String> cells=new ArrayList<>();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))){
            workbook.getSheetAt(0).forEach(row->row.forEach(cell->cells.add(cell.getStringCellValue())));
            assertThat(workbook.getSheetAt(0).getPrintSetup().getLandscape()).isTrue();
        }
        assertThat(cells).anyMatch(value->value.contains("DRAFT — CHƯA CÔNG BỐ"));
        assertThat(cells).anyMatch(value->value.startsWith("'=HYPERLINK")&&value.contains("Tiếng Việt"));
        assertThat(cells.stream().filter(value->List.of("Công tác chuyên môn","Cơ sở vật chất – thiết bị – văn phòng","Đoàn Thanh niên – Hội LHTN","Giáo viên chủ nhiệm","Giáo viên").contains(value)).count()).isEqualTo(5);

        UUID auditId=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,new_value,correlation_id)
                VALUES (?,?,'USER','SecurityProbe',?,'SECRET_TEST','{\"password\":\"do-not-leak\",\"safe\":\"visible\"}'::jsonb,?)
                """,auditId,ADMIN_ID,UUID.randomUUID(),UUID.randomUUID());
        var audit=mvc.perform(get("/api/v1/audit-logs").param("entityType","SecurityProbe").cookie(admin)).andExpect(status().isOk()).andReturn();
        String auditBody=audit.getResponse().getContentAsString();
        assertThat(auditBody).contains("[REDACTED]").contains("visible").doesNotContain("do-not-leak");
        assertThatThrownBy(()->jdbc.update("UPDATE audit_logs SET action='TAMPERED' WHERE id=?",auditId)).hasMessageContaining("append-only");

        var userLogin=mvc.perform(login("teacher@example.edu.vn","Teacher@2026")).andExpect(status().isOk()).andReturn();
        Cookie user=userLogin.getResponse().getCookie("session");
        mvc.perform(get("/api/v1/audit-logs").cookie(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/weekly-plans/{id}/export",planId).cookie(user)).andExpect(status().isForbidden());
    }

    private String updateRequest(long version, boolean removeLastSession) throws Exception {
        List<Map<String, Object>> sections = new ArrayList<>();
        List<String> types = List.of("ACADEMIC_AFFAIRS", "FACILITIES_OFFICE", "YOUTH_UNION", "HOMEROOM_TEACHERS", "TEACHERS");
        for (int index = 0; index < types.size(); index++) {
            List<Map<String, Object>> targets = index == 0
                    ? List.of(Map.of("targetType", "ROLE", "targetId", ROLE_ID)) : List.of();
            sections.add(Map.of("sectionType", types.get(index), "content", "Nội dung mục " + (index + 1),
                    "displayOrder", index + 1, "targets", targets));
        }
        List<Map<String, Object>> sessions = new ArrayList<>();
        int order = 0;
        for (LocalDate date = LocalDate.of(2026, 9, 7); !date.isAfter(LocalDate.of(2026, 9, 13)); date = date.plusDays(1)) {
            sessions.add(Map.of("date", date.toString(), "session", "MORNING", "baseContent", "Nền " + order++));
            sessions.add(Map.of("date", date.toString(), "session", "AFTERNOON", "baseContent", "Nền " + order++));
        }
        if (removeLastSession) sessions.removeLast();
        Map<String, Object> duty = new LinkedHashMap<>();
        duty.put("morningClassId", CLASS_ID); duty.put("afternoonClassId", null);
        return json.writeValueAsString(Map.of("version", version, "sections", sections,
                "dutyClasses", duty, "daySessions", sessions));
    }

    private void insertWeek(UUID id, int sequence, int display, LocalDate start, LocalDate end) {
        jdbc.update("""
                INSERT INTO school_weeks(id,academic_year_id,sequence_number,display_number,week_type,start_date,end_date)
                VALUES (?,?,?,?, 'STUDY',?,?)
                """, id, YEAR_ID, sequence, display, start, end);
    }
    private void insertUser(UUID id, String email, String role, String name, String password) {
        jdbc.update("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_at)
                VALUES (?,?,?,?,?,?,'ACTIVE',?,now())
                """, id, email, email, passwords.encode(password), name, role, DEPARTMENT_ID);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login").with(request -> { request.setRemoteAddr("test-" + UUID.randomUUID()); return request; })
                .header("Origin", ORIGIN).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
