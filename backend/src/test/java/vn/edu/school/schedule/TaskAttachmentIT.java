package vn.edu.school.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class TaskAttachmentIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final Path STORAGE_ROOT = temporaryStorage();
    private static final String ORIGIN = "http://localhost:3000";
    private static final UUID DEPARTMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ASSIGNEE_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID YEAR_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID WEEK_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000007");
    private static final UUID TASK_ID = UUID.fromString("10000000-0000-0000-0000-000000000008");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.session-pepper", () -> "task-attachment-test-pepper-at-least-32-characters");
        registry.add("app.task-attachments.local-root", STORAGE_ROOT::toString);
        registry.add("app.outbox.initial-delay-ms", () -> "3600000");
        registry.add("app.reminder.initial-delay-ms", () -> "3600000");
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        deleteStoredFiles();
        jdbc.execute("TRUNCATE TABLE audit_logs");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM task_attachments");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM weekly_plans");
        jdbc.update("DELETE FROM school_weeks");
        jdbc.update("DELETE FROM academic_years");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM departments");
        jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)", DEPARTMENT_ID, "Văn phòng", "van phong");
        insertUser(ADMIN_ID, "admin-attachment@example.test", "ADMIN", "Quản trị", "Admin@2026");
        insertUser(ASSIGNEE_ID, "assignee@example.test", "USER", "Người nhận", "Teacher@2026");
        insertUser(OTHER_USER_ID, "other-attachment@example.test", "USER", "Người khác", "Other@2026");
        jdbc.update("INSERT INTO academic_years(id,name,start_date,created_by) VALUES (?,?,?,?)",
                YEAR_ID, "2030-2031", LocalDate.of(2030, 8, 1), ADMIN_ID);
        jdbc.update("""
                INSERT INTO school_weeks(id,academic_year_id,sequence_number,display_number,week_type,start_date,end_date)
                VALUES (?,?,1,1,'STUDY',?,?)
                """, WEEK_ID, YEAR_ID, LocalDate.of(2030, 8, 5), LocalDate.of(2030, 8, 11));
        jdbc.update("INSERT INTO weekly_plans(id,school_week_id,created_by) VALUES (?,?,?)", PLAN_ID, WEEK_ID, ADMIN_ID);
        jdbc.update("""
                INSERT INTO tasks(id,weekly_plan_id,assignee_user_id,title,due_at,created_by)
                VALUES (?,?,?,?,?,?)
                """, TASK_ID, PLAN_ID, ASSIGNEE_ID, "Chuẩn bị báo cáo", Timestamp.from(Instant.parse("2030-08-10T10:00:00Z")), ADMIN_ID);
    }

    @AfterAll
    static void stop() throws Exception {
        POSTGRES.stop();
        if (Files.exists(STORAGE_ROOT)) try (var paths = Files.walk(STORAGE_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
        }
    }

    @Test
    void uploadListDownloadUnicodeIdorDeleteAndAudit() throws Exception {
        Session admin = login("admin-attachment@example.test", "Admin@2026");
        Session assignee = login("assignee@example.test", "Teacher@2026");
        Session other = login("other-attachment@example.test", "Other@2026");
        MockMultipartFile docx = new MockMultipartFile("file", "Mẫu báo cáo tháng 8.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx());

        mvc.perform(multipart("/api/v1/tasks/{id}/attachments", TASK_ID).file(docx).cookie(admin.cookie()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(multipart("/api/v1/tasks/{id}/attachments", TASK_ID).file(docx)
                        .cookie(assignee.cookie()).header("X-CSRF-Token", assignee.csrf()))
                .andExpect(status().isForbidden());
        MvcResult uploaded = mvc.perform(multipart("/api/v1/tasks/{id}/attachments", TASK_ID).file(docx)
                        .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalName").value("Mẫu báo cáo tháng 8.docx"))
                .andExpect(jsonPath("$.data.checksum").isNotEmpty()).andReturn();
        String attachmentId = json.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asText();
        String storageKey = jdbc.queryForObject("SELECT storage_key FROM task_attachments WHERE id=?", String.class, UUID.fromString(attachmentId));
        assertThat(storageKey).matches("tasks/" + TASK_ID + "/[0-9a-f-]+\\.docx").doesNotContain("Mẫu");

        mvc.perform(get("/api/v1/tasks/{id}/attachments", TASK_ID).cookie(other.cookie()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("TASK_ATTACHMENT_FORBIDDEN"));
        mvc.perform(get("/api/v1/task-attachments/{id}/download", attachmentId).cookie(other.cookie()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tasks/{id}/attachments", TASK_ID).cookie(assignee.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/task-attachments/{id}/download", attachmentId).cookie(assignee.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")));
        mvc.perform(get("/api/v1/task-attachments/{id}/download", attachmentId).cookie(admin.cookie()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/task-attachments/{id}", attachmentId).cookie(assignee.cookie())
                        .header("X-CSRF-Token", assignee.csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/task-attachments/{id}", attachmentId).cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM task_attachments WHERE id=?", Boolean.class,
                UUID.fromString(attachmentId))).isTrue();
        assertThat(Files.exists(STORAGE_ROOT.resolve(storageKey))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_type='TaskAttachment'", Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidExecutableOversizeCountAndTotalLimitsAreRejectedWithoutOrphans() throws Exception {
        Session admin = login("admin-attachment@example.test", "Admin@2026");
        mvc.perform(multipart("/api/v1/tasks/{id}/attachments", TASK_ID)
                        .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1}))
                        .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"));
        mvc.perform(multipart("/api/v1/tasks/{id}/attachments", TASK_ID)
                        .file(new MockMultipartFile("file", "large.txt", "text/plain", new byte[20 * 1024 * 1024 + 1]))
                        .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));

        for (int index = 0; index < 10; index++) insertMetadata(index, 1);
        uploadText(admin).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_COUNT_EXCEEDED"));
        assertThat(storedRegularFileCount()).isZero();

        jdbc.update("DELETE FROM task_attachments");
        insertMetadata(20, 100L * 1024 * 1024);
        uploadText(admin).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ATTACHMENT_TOTAL_SIZE_EXCEEDED"));
        assertThat(storedRegularFileCount()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions uploadText(Session admin) throws Exception {
        return mvc.perform(multipart("/api/v1/tasks/{id}/attachments", TASK_ID)
                .file(new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
                .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf()));
    }

    private void insertMetadata(int index, long size) {
        jdbc.update("""
                INSERT INTO task_attachments(id,task_id,uploaded_by,original_name,storage_key,content_type,file_size)
                VALUES (?,?,?,?,?,'text/plain',?)
                """, UUID.randomUUID(), TASK_ID, ADMIN_ID, "seed-" + index + ".txt",
                "tasks/" + TASK_ID + "/" + UUID.randomUUID() + ".txt", size);
    }

    private Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").header("Origin", ORIGIN)
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session(result.getResponse().getCookie("session"), result.getResponse().getHeader("X-CSRF-Token"));
    }

    private void insertUser(UUID id, String email, String role, String name, String password) {
        jdbc.update("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_at)
                VALUES (?,?,?,?,?,?,'ACTIVE',?,now())
                """, id, email, email, passwords.encode(password), name, role, DEPARTMENT_ID);
    }

    private static byte[] docx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private long storedRegularFileCount() throws Exception {
        try (var paths = Files.walk(STORAGE_ROOT)) { return paths.filter(Files::isRegularFile).count(); }
    }

    private void deleteStoredFiles() throws Exception {
        if (!Files.exists(STORAGE_ROOT)) return;
        try (var paths = Files.walk(STORAGE_ROOT)) {
            paths.filter(Files::isRegularFile).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
        }
    }

    private static Path temporaryStorage() {
        try { return Files.createTempDirectory("schedule-task-attachments-"); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    private record Session(Cookie cookie, String csrf) { }
}
