package vn.edu.school.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
class Phase3AcademicCalendarIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final String ORIGIN = "http://localhost:3000";
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000403");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.session-pepper", () -> "phase-3-integration-pepper-at-least-32-characters");
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM school_weeks");
        jdbc.update("DELETE FROM school_classes");
        jdbc.update("DELETE FROM academic_years");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM departments");
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)",
                DEPARTMENT_ID, "Văn phòng", "van phong");
        insertUser(ADMIN_ID, "admin@example.edu.vn", "ADMIN", "Hiệu trưởng", "Admin@2026");
        insertUser(USER_ID, "teacher@example.edu.vn", "USER", "Nguyễn Văn An", "Teacher@2026");
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @Test
    void academicCalendarGeneratesEditsWarnsAuditsAndEnforcesSecurity() throws Exception {
        mvc.perform(get("/api/v1/academic-years")).andExpect(status().isUnauthorized());
        var login = mvc.perform(login("admin@example.edu.vn", "Admin@2026"))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("session");
        String csrf = login.getResponse().getHeader("X-CSRF-Token");

        mvc.perform(post("/api/v1/academic-years").cookie(cookie).contentType("application/json")
                        .content(yearCreate("2026-2027", "2026-08-17", true, true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/academic-years").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearCreate("2026/2027", "2026-08-17", true, true)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("ACADEMIC_YEAR_NAME_INVALID"));
        mvc.perform(post("/api/v1/academic-years").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearCreate("2026-2027", "2026-08-17", true, true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.weekCount").value(39))
                .andExpect(jsonPath("$.data.version").value(0));

        UUID firstYear = jdbc.queryForObject("SELECT id FROM academic_years WHERE name='2026-2027'", UUID.class);
        mvc.perform(get("/api/v1/academic-years/{id}/weeks", firstYear).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(39))
                .andExpect(jsonPath("$.data[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.data[0].weekType").value("ORIENTATION"))
                .andExpect(jsonPath("$.data[1].displayNumber").value(2))
                .andExpect(jsonPath("$.data[2].sequenceNumber").value(3))
                .andExpect(jsonPath("$.data[2].displayNumber").value(1))
                .andExpect(jsonPath("$.data[2].weekType").value("STUDY"))
                .andExpect(jsonPath("$.data[38].displayNumber").value(37))
                .andExpect(jsonPath("$.data[0].startDate").value("2026-08-17"))
                .andExpect(jsonPath("$.data[0].endDate").value("2026-08-23"));
        mvc.perform(post("/api/v1/academic-years/{id}/weeks/generate", firstYear).cookie(cookie)
                        .header("X-CSRF-Token", csrf).contentType("application/json").content("{\"count\":39}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("WEEKS_EXIST"));
        mvc.perform(post("/api/v1/academic-years").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearCreate("2026-2027", "2026-08-18", false, false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ACADEMIC_YEAR_NAME_EXISTS"));
        mvc.perform(post("/api/v1/academic-years").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearCreate("2027-2028", "2027-08-16", true, false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ACTIVE_ACADEMIC_YEAR_EXISTS"));
        mvc.perform(post("/api/v1/academic-years").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearCreate("2027-2028", "2027-08-16", false, false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.isActive").value(false));
        UUID secondYear = jdbc.queryForObject("SELECT id FROM academic_years WHERE name='2027-2028'", UUID.class);
        mvc.perform(patch("/api/v1/academic-years/{id}", secondYear).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearUpdate("2027-2028", "2027-08-16", true, 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ACTIVE_ACADEMIC_YEAR_EXISTS"));

        UUID thirdWeek = jdbc.queryForObject(
                "SELECT id FROM school_weeks WHERE academic_year_id=? AND sequence_number=3", UUID.class, firstYear);
        mvc.perform(patch("/api/v1/weeks/{id}", thirdWeek).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json")
                        .content(weekUpdate(1, "STUDY", "2026-08-23", "2026-08-29", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.warnings[0]").value("WEEK_OVERLAP"));
        mvc.perform(patch("/api/v1/weeks/{id}", thirdWeek).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json")
                        .content(weekUpdate(1, "STUDY", "2026-08-23", "2026-08-29", 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
        UUID fourthWeek = jdbc.queryForObject(
                "SELECT id FROM school_weeks WHERE academic_year_id=? AND sequence_number=4", UUID.class, firstYear);
        mvc.perform(patch("/api/v1/weeks/{id}", fourthWeek).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json")
                        .content(weekUpdate(2, "STUDY", "2026-09-10", "2026-09-01", 0)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("WEEK_DATE_INVALID"));

        UUID classId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO school_classes(id,academic_year_id,name,normalized_name,grade)
                VALUES (?,?,?,?,?)
                """, classId, firstYear, "10A1", "10a1", 10);
        mvc.perform(patch("/api/v1/academic-years/{id}", firstYear).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearUpdate("2026-2027", "2026-08-17", false, 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ACADEMIC_YEAR_IN_USE"));
        jdbc.update("UPDATE school_classes SET is_active=false WHERE id=?", classId);
        mvc.perform(patch("/api/v1/academic-years/{id}", firstYear).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearUpdate("2026-2027", "2026-08-17", false, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(patch("/api/v1/academic-years/{id}", secondYear).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(yearUpdate("2027-2028", "2027-08-16", true, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isActive").value(true));

        Integer audits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE entity_type IN ('AcademicYear','SchoolWeek')", Integer.class);
        assertThat(audits).isGreaterThanOrEqualTo(6);
        var userLogin = mvc.perform(login("teacher@example.edu.vn", "Teacher@2026"))
                .andExpect(status().isOk()).andReturn();
        Cookie userCookie = userLogin.getResponse().getCookie("session");
        String userCsrf = userLogin.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(get("/api/v1/academic-years").cookie(userCookie)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/academic-years").cookie(userCookie).header("X-CSRF-Token", userCsrf)
                        .contentType("application/json").content(yearCreate("2028-2029", "2028-08-14", false, false)))
                .andExpect(status().isForbidden());
    }

    private void insertUser(UUID id, String email, String role, String name, String password) {
        jdbc.update("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_at)
                VALUES (?,?,?,?,?,?,'ACTIVE',?,now())
                """, id, email, email, passwords.encode(password), name, role, DEPARTMENT_ID);
    }

    private String yearCreate(String name, String start, boolean active, boolean generate) {
        return "{\"name\":\"" + name + "\",\"startDate\":\"" + start + "\",\"isActive\":" + active
                + ",\"generateWeeks\":" + generate + "}";
    }
    private String yearUpdate(String name, String start, boolean active, long version) {
        return "{\"name\":\"" + name + "\",\"startDate\":\"" + start + "\",\"isActive\":" + active
                + ",\"version\":" + version + "}";
    }
    private String weekUpdate(int display, String type, String start, String end, long version) {
        return "{\"displayNumber\":" + display + ",\"weekType\":\"" + type + "\",\"startDate\":\""
                + start + "\",\"endDate\":\"" + end + "\",\"version\":" + version + "}";
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login").header("Origin", ORIGIN).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
