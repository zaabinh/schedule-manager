package vn.edu.school.schedule.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.school.schedule.auth.api.CurrentUser;
import vn.edu.school.schedule.auth.api.LoginRequest;
import vn.edu.school.schedule.auth.api.LoginResult;
import vn.edu.school.schedule.auth.api.RegisterRequest;
import vn.edu.school.schedule.auth.api.RegistrationResponse;
import vn.edu.school.schedule.auth.api.ResourceRef;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.security.SecretHasher;
import vn.edu.school.schedule.shared.security.SessionAuthenticator;

@Service
public class AuthService implements SessionAuthenticator {
    private static final Duration IDLE_TIMEOUT = Duration.ofHours(8);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(24);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final PasswordPolicy passwordPolicy;
    private final AuthRateLimiter rateLimiter;
    private final SecretHasher secrets;
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, PasswordPolicy passwordPolicy,
                       AuthRateLimiter rateLimiter, SecretHasher secrets) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.secrets = secrets;
        this.dummyHash = passwords.encode("not-a-real-password-value");
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        rateLimiter.check("register:" + clientIp + ":" + secrets.hash(email));
        passwordPolicy.validate(request.password());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO users (id,email,normalized_email,password_hash,display_name,system_role,status,version)
                    VALUES (?,?,?,?,?,'USER','PENDING',0)
                    """, id, request.email().trim(), email, passwords.encode(request.password()), request.displayName().trim());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được sử dụng.");
        }
        return new RegistrationResponse(id, "PENDING");
    }

    @Transactional
    public LoginResult login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        rateLimiter.check("login:" + clientIp + ":" + secrets.hash(email));
        List<LoginRow> rows = jdbc.query("""
                SELECT id,password_hash,status,system_role FROM users WHERE normalized_email=?
                """, (rs, rowNum) -> new LoginRow(
                rs.getObject("id", UUID.class), rs.getString("password_hash"),
                rs.getString("status"), rs.getString("system_role")), email);
        LoginRow row = rows.isEmpty() ? null : rows.getFirst();
        boolean passwordValid = passwords.matches(request.password(), row == null ? dummyHash : row.passwordHash());
        if (row == null || !passwordValid) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.");
        }

        if(!"ACTIVE".equals(row.status())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tài khoản chưa được Admin xác thực, vui lòng liên hệ tới ban quản trị.");
        }

        String sessionToken = secrets.randomToken();
        String csrfToken = secrets.randomToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO auth_sessions (id_hash,user_id,csrf_secret_hash,expires_at,last_seen_at)
                VALUES (?,?,?,?,?)
                """, secrets.hash(sessionToken), row.id(), secrets.hash(csrfToken), now.plus(ABSOLUTE_TIMEOUT), now);
        return new LoginResult(loadCurrentUser(row.id()), sessionToken, csrfToken);
    }

    @Transactional
    @Override
    public AuthenticatedUser authenticate(String rawSession) {
        if (rawSession == null || rawSession.isBlank()) return null;
        String hash = secrets.hash(rawSession);
        List<AuthenticatedUser> rows = jdbc.query("""
                SELECT u.id,u.system_role,s.id_hash,s.csrf_secret_hash
                FROM auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE s.id_hash=? AND s.revoked_at IS NULL AND s.expires_at>now()
                  AND s.last_seen_at>? AND u.status='ACTIVE'
                """, (rs, rowNum) -> new AuthenticatedUser(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                hash, OffsetDateTime.now(ZoneOffset.UTC).minus(IDLE_TIMEOUT));
        if (rows.isEmpty()) return null;
        jdbc.update("UPDATE auth_sessions SET last_seen_at=now() WHERE id_hash=?", hash);
        return rows.getFirst();
    }

    @Override
    public boolean validCsrf(AuthenticatedUser user, String rawToken) {
        return user != null && secrets.matchesHash(rawToken, user.csrfHash());
    }

    @Transactional
    public void logout(AuthenticatedUser user) {
        jdbc.update("UPDATE auth_sessions SET revoked_at=now() WHERE id_hash=? AND revoked_at IS NULL", user.sessionHash());
    }

    @Transactional
    public String rotateCsrf(AuthenticatedUser user) {
        String token = secrets.randomToken();
        jdbc.update("UPDATE auth_sessions SET csrf_secret_hash=? WHERE id_hash=? AND revoked_at IS NULL",
                secrets.hash(token), user.sessionHash());
        return token;
    }

    public CurrentUser loadCurrentUser(UUID userId) {
        CurrentUserBase base = jdbc.queryForObject("""
                SELECT u.id,u.email,u.display_name,u.system_role,u.status,u.version,
                       d.id department_id,d.name department_name
                FROM users u LEFT JOIN departments d ON d.id=u.department_id WHERE u.id=?
                """, this::mapBase, userId);
        if (base == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng.");
        List<ResourceRef> roles = jdbc.query("""
                SELECT r.id,r.name FROM business_roles r JOIN user_roles ur ON ur.business_role_id=r.id
                WHERE ur.user_id=? ORDER BY r.name
                """, (rs, rowNum) -> new ResourceRef(rs.getObject(1, UUID.class), rs.getString(2)), userId);
        List<ResourceRef> classes = jdbc.query("""
                SELECT id,name FROM school_classes WHERE homeroom_teacher_id=? AND is_active=true
                """, (rs, rowNum) -> new ResourceRef(rs.getObject(1, UUID.class), rs.getString(2)), userId);
        ResourceRef department = base.departmentId() == null ? null : new ResourceRef(base.departmentId(), base.departmentName());
        return new CurrentUser(base.id(), base.email(), base.displayName(), base.systemRole(), base.status(),
                department, roles, classes.isEmpty() ? null : classes.getFirst(), base.version());
    }

    private CurrentUserBase mapBase(ResultSet rs, int rowNum) throws SQLException {
        return new CurrentUserBase(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getString("display_name"), rs.getString("system_role"), rs.getString("status"),
                rs.getLong("version"), rs.getObject("department_id", UUID.class), rs.getString("department_name"));
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record LoginRow(UUID id, String passwordHash, String status, String systemRole) { }
    private record CurrentUserBase(UUID id, String email, String displayName, String systemRole, String status,
                                   long version, UUID departmentId, String departmentName) { }
}
