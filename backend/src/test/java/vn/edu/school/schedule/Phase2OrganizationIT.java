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
class Phase2OrganizationIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final String ORIGIN = "http://localhost:3000";
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID TEACHER_ONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID TEACHER_TWO_ID = UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID YEAR_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");
    private static final UUID PROTECTED_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.session-pepper", () -> "phase-2-integration-pepper-at-least-32-characters");
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
        jdbc.update("DELETE FROM school_classes");
        jdbc.update("DELETE FROM academic_years");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM departments");
        jdbc.update("DELETE FROM business_roles WHERE is_protected=false");
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)", DEPARTMENT_ID, "Văn phòng", "van phong");
        insertUser(ADMIN_ID, "admin@example.edu.vn", "ADMIN", "Hiệu trưởng", "Admin@2026");
        insertUser(TEACHER_ONE_ID, "teacher1@example.edu.vn", "USER", "Nguyễn Văn An", "Teacher@2026");
        insertUser(TEACHER_TWO_ID, "teacher2@example.edu.vn", "USER", "Lê Thu Mai", "Teacher@2026");
        jdbc.update("INSERT INTO academic_years(id,name,start_date,created_by) VALUES (?,?,?,?)",
                YEAR_ID, "2026-2027", LocalDate.of(2026, 8, 17), ADMIN_ID);
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @Test
    void organizationCrudEnforcesVersionProtectionAuditAndHomeroomConstraint() throws Exception {
        var login = mvc.perform(login("admin@example.edu.vn", "Admin@2026")).andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("session");
        String csrf = login.getResponse().getHeader("X-CSRF-Token");

        mvc.perform(post("/api/v1/departments").cookie(cookie).contentType("application/json")
                        .content("{\"name\":\"Tổ Toán\",\"description\":\"Tổ chuyên môn\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/departments").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json")
                        .content("{\"name\":\"Tổ Toán\",\"description\":\"Tổ chuyên môn\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(0));
        UUID mathId = jdbc.queryForObject("SELECT id FROM departments WHERE normalized_name='to toan'", UUID.class);
        assertThat(mathId).isNotNull();
        mvc.perform(post("/api/v1/departments").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content("{\"name\":\"  TO TOÁN  \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DEPARTMENT_NAME_EXISTS"));
        String departmentUpdate = "{\"name\":\"Tổ Toán\",\"description\":\"Đã cập nhật\",\"isActive\":false,\"version\":0}";
        mvc.perform(patch("/api/v1/departments/{id}", mathId).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(departmentUpdate))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isActive").value(false))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(patch("/api/v1/departments/{id}", mathId).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(departmentUpdate))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/departments").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(2));

        mvc.perform(post("/api/v1/business-roles").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content("{\"name\":\"Phụ trách thư viện\"}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/business-roles/{id}", PROTECTED_ROLE_ID).cookie(cookie)
                        .header("X-CSRF-Token", csrf).contentType("application/json")
                        .content("{\"name\":\"Ban giám hiệu\",\"isActive\":false,\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ROLE_PROTECTED"));

        mvc.perform(get("/api/v1/organization/options").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.academicYears[0].name").value("2026-2027"))
                .andExpect(jsonPath("$.data.availableTeachers.length()").value(2));
        mvc.perform(post("/api/v1/classes").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(classRequest("11A2", 11, TEACHER_ONE_ID)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.homeroomTeacher.name").value("Nguyễn Văn An"));
        UUID firstClass = jdbc.queryForObject("SELECT id FROM school_classes WHERE normalized_name='11a2'", UUID.class);
        mvc.perform(post("/api/v1/classes").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(classRequest("11B5", 11, TEACHER_ONE_ID)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("HOMEROOM_CONFLICT"));
        mvc.perform(post("/api/v1/classes").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(classRequest("9A1", 9, TEACHER_TWO_ID)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(patch("/api/v1/classes/{id}", firstClass).cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(classUpdate("11A2", 11, TEACHER_ONE_ID, false, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isActive").value(false));
        mvc.perform(post("/api/v1/classes").cookie(cookie).header("X-CSRF-Token", csrf)
                        .contentType("application/json").content(classRequest("11B5", 11, TEACHER_ONE_ID)))
                .andExpect(status().isCreated());

        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type IN ('Department','BusinessRole','SchoolClass')", Integer.class);
        assertThat(audits).isGreaterThanOrEqualTo(5);
        mvc.perform(get("/api/v1/classes")).andExpect(status().isUnauthorized());
        var userLogin = mvc.perform(login("teacher2@example.edu.vn", "Teacher@2026")).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/departments").cookie(userLogin.getResponse().getCookie("session")))
                .andExpect(status().isForbidden());
    }

    private void insertUser(UUID id, String email, String role, String name, String password) {
        jdbc.update("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_at)
                VALUES (?,?,?,?,?,?,'ACTIVE',?,now())
                """, id, email, email, passwords.encode(password), name, role, DEPARTMENT_ID);
    }

    private String classRequest(String name, int grade, UUID teacher) {
        return "{\"academicYearId\":\"" + YEAR_ID + "\",\"name\":\"" + name + "\",\"grade\":" + grade
                + ",\"homeroomTeacherId\":\"" + teacher + "\"}";
    }

    private String classUpdate(String name, int grade, UUID teacher, boolean active, int version) {
        return "{\"academicYearId\":\"" + YEAR_ID + "\",\"name\":\"" + name + "\",\"grade\":" + grade
                + ",\"homeroomTeacherId\":\"" + teacher + "\",\"isActive\":" + active + ",\"version\":" + version + "}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login").header("Origin", ORIGIN).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
