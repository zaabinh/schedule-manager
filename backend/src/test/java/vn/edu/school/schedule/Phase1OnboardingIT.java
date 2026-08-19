package vn.edu.school.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
class Phase1OnboardingIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final String ORIGIN = "http://localhost:3000";
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID LEADERSHIP_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.session-pepper", () -> "integration-test-pepper-at-least-32-characters");
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
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)", DEPARTMENT_ID, "Văn phòng", "van phong");
        jdbc.update("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_at)
                VALUES (?,?,?,?,?,'ADMIN','ACTIVE',?,now())
                """, ADMIN_ID, "admin@example.edu.vn", "admin@example.edu.vn",
                passwords.encode("correct horse battery staple"), "Hiệu trưởng", DEPARTMENT_ID);
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @Test
    void onboarding_enforcesApprovalSessionCsrfAndSystemRole() throws Exception {
        String userEmail = "teacher@example.edu.vn";
        String userPassword = "MậtKhẩu@2026";

        mvc.perform(post("/api/v1/auth/register").header("Origin", ORIGIN).contentType("application/json")
                        .content("""
                                {"email":"teacher@example.edu.vn","password":"MậtKhẩu@2026","displayName":"Nguyễn Văn An"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE normalized_email=?", UUID.class, userEmail);
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, userId);
        assertThat(storedHash).doesNotContain(userPassword);

        mvc.perform(login(userEmail, userPassword)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        var adminLogin = mvc.perform(login("admin@example.edu.vn", "correct horse battery staple"))
                .andExpect(status().isOk()).andExpect(header().exists("X-CSRF-Token")).andReturn();
        Cookie adminCookie = adminLogin.getResponse().getCookie("session");
        String adminCsrf = adminLogin.getResponse().getHeader("X-CSRF-Token");
        assertThat(adminCookie).isNotNull();
        assertThat(adminCookie.isHttpOnly()).isTrue();
        assertThat(adminCookie.getValue()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT id_hash FROM auth_sessions WHERE user_id=?", String.class, ADMIN_ID))
                .isNotEqualTo(adminCookie.getValue());

        mvc.perform(get("/api/v1/users").param("status", "PENDING").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value(userEmail))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        String approval = "{\"departmentId\":\"" + DEPARTMENT_ID + "\",\"businessRoleIds\":[\""
                + LEADERSHIP_ROLE_ID + "\"],\"version\":0}";
        mvc.perform(patch("/api/v1/users/{id}/approval", userId).cookie(adminCookie)
                        .contentType("application/json").content(approval))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(patch("/api/v1/users/{id}/approval", userId).cookie(adminCookie)
                        .header("X-CSRF-Token", adminCsrf).contentType("application/json").content(approval))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.systemRole").value("USER"));

        var userLogin = mvc.perform(login(userEmail, userPassword)).andExpect(status().isOk()).andReturn();
        Cookie userCookie = userLogin.getResponse().getCookie("session");
        String userCsrf = userLogin.getResponse().getHeader("X-CSRF-Token");
        var me = mvc.perform(get("/api/v1/auth/me").cookie(userCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.systemRole").value("USER")).andReturn();
        userCsrf = me.getResponse().getHeader("X-CSRF-Token");
        mvc.perform(get("/api/v1/users").cookie(userCookie)).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/logout").cookie(userCookie).header("X-CSRF-Token", userCsrf))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(userCookie)).andExpect(status().isUnauthorized());

        userLogin = mvc.perform(login(userEmail, userPassword)).andExpect(status().isOk()).andReturn();
        userCookie = userLogin.getResponse().getCookie("session");
        mvc.perform(patch("/api/v1/users/{id}/status", userId).cookie(adminCookie)
                        .header("X-CSRF-Token", adminCsrf).contentType("application/json")
                        .content("{\"status\":\"INACTIVE\",\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INACTIVE"));
        mvc.perform(get("/api/v1/auth/me").cookie(userCookie)).andExpect(status().isUnauthorized());
        mvc.perform(login(userEmail, userPassword)).andExpect(status().isUnauthorized());
    }

    @Test
    void guestEndpointsRejectInvalidOriginInputAndDuplicateRegistration() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("{\"email\":\"missing-origin@example.edu.vn\",\"password\":\"a sufficiently long passphrase\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORIGIN_INVALID"));
        mvc.perform(post("/api/v1/auth/register").header("Referer", "not a valid URI [")
                        .contentType("application/json")
                        .content("{\"email\":\"bad-referer@example.edu.vn\",\"password\":\"a sufficiently long passphrase\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ORIGIN_INVALID"));
        mvc.perform(post("/api/v1/auth/register").header("Origin", ORIGIN).contentType("application/json")
                        .content("{\"email\":\"short@example.edu.vn\",\"password\":\"too short\",\"displayName\":\"Test User\"}"))
                .andExpect(status().isUnprocessableContent());

        String registration = "{\"email\":\"duplicate@example.edu.vn\",\"password\":\"Valid@Pass\",\"displayName\":\"Test User\"}";
        mvc.perform(post("/api/v1/auth/register").header("Origin", ORIGIN).contentType("application/json")
                        .content(registration)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").header("Origin", ORIGIN).contentType("application/json")
                        .content(registration))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMAIL_EXISTS"));
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login").header("Origin", ORIGIN).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
