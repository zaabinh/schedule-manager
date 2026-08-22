package vn.edu.school.schedule.auth;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrapper implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;
    private final TransactionTemplate transactions;
    private final ConfigurableApplicationContext applicationContext;
    private final boolean provisioningMode;
    private final String email;
    private final String password;
    private final String displayName;
    private final String departmentName;

    public AdminBootstrapper(JdbcTemplate jdbc, PasswordEncoder passwords, PasswordPolicy policy,
            PlatformTransactionManager transactionManager, ConfigurableApplicationContext applicationContext,
            @Value("${app.bootstrap-admin.provisioning-mode:false}") boolean provisioningMode,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.bootstrap-admin.display-name:Quản trị hệ thống}") String displayName,
            @Value("${app.bootstrap-admin.department-name:Văn phòng}") String departmentName) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.policy = policy;
        this.transactions = new TransactionTemplate(transactionManager);
        this.applicationContext = applicationContext;
        this.provisioningMode = provisioningMode;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
        this.departmentName = departmentName;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(ignored -> bootstrap());
        if (provisioningMode) applicationContext.close();
    }

    private void bootstrap() {
        String normalizedEmail = AuthService.normalizeEmail(email);
        Integer matchingAdmin = jdbc.queryForObject("""
                SELECT count(*) FROM users
                WHERE normalized_email=? AND system_role='ADMIN' AND status='ACTIVE'
                """, Integer.class, normalizedEmail);
        if (matchingAdmin != null && matchingAdmin > 0) return;

        Integer admins = jdbc.queryForObject("SELECT count(*) FROM users WHERE system_role='ADMIN' AND status='ACTIVE'", Integer.class);
        if (admins != null && admins >= 2) throw new IllegalStateException("At most two active Admin accounts are allowed");
        if (email.isBlank() || password.isBlank()) throw new IllegalStateException("Bootstrap Admin credentials are required");
        Integer existingAccount = jdbc.queryForObject("SELECT count(*) FROM users WHERE normalized_email=?", Integer.class, normalizedEmail);
        if (existingAccount != null && existingAccount > 0) {
            throw new IllegalStateException("Bootstrap Admin email already belongs to another account");
        }
        policy.validate(password);
        String normalizedDepartment = normalize(departmentName);
        UUID departmentId = jdbc.query("SELECT id FROM departments WHERE normalized_name=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, normalizedDepartment);
        if (departmentId == null) {
            departmentId = UUID.randomUUID();
            jdbc.update("INSERT INTO departments(id,name,normalized_name) VALUES (?,?,?)",
                    departmentId, departmentName.trim(), normalizedDepartment);
        }
        UUID adminId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,department_id,approved_at)
                VALUES (?,?,?,?,?,'ADMIN','ACTIVE',?,now())
                """, adminId, email.trim(), normalizedEmail, passwords.encode(password), displayName.trim(), departmentId);
        jdbc.update("""
                INSERT INTO audit_logs(id,actor_type,entity_type,entity_id,action,new_value,correlation_id)
                VALUES (?,'SYSTEM','User',?,'ADMIN_BOOTSTRAPPED',CAST('{"systemRole":"ADMIN","status":"ACTIVE"}' AS jsonb),?)
                """, UUID.randomUUID(), adminId, UUID.randomUUID());
    }

    private String normalize(String value) {
        String noMarks = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
