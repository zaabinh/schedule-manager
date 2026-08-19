package vn.edu.school.schedule.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.school.schedule.audit.api.AuditLogResponse;
import vn.edu.school.schedule.shared.api.ApiException;

@Service
public class AuditLogService {
    private static final Pattern SECRET = Pattern.compile("(?i)(\\\"[^\\\"]*(?:password|token|secret|pepper|cookie)[^\\\"]*\\\"\\s*:\\s*)\\\"(?:\\\\.|[^\\\"])*\\\"");
    private final JdbcTemplate jdbc;
    public AuditLogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AuditLogResponse> list(UUID actorId, String entityType, String action, Instant from, Instant to, int size) {
        if (size < 1 || size > 200) throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PAGE_SIZE_INVALID", "Size phải từ 1 đến 200.");
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "DATE_RANGE_INVALID", "Khoảng thời gian không hợp lệ.");
        StringBuilder sql = new StringBuilder("""
                SELECT a.id,a.created_at,COALESCE(u.display_name,'SYSTEM'),a.entity_type,a.entity_id,a.action,
                       a.old_value::text,a.new_value::text,a.correlation_id
                FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (actorId != null) { sql.append(" AND a.actor_user_id=?"); args.add(actorId); }
        if (entityType != null && !entityType.isBlank()) { sql.append(" AND a.entity_type=?"); args.add(entityType.trim()); }
        if (action != null && !action.isBlank()) { sql.append(" AND a.action=?"); args.add(action.trim()); }
        if (from != null) { sql.append(" AND a.created_at>=?"); args.add(java.sql.Timestamp.from(from)); }
        if (to != null) { sql.append(" AND a.created_at<=?"); args.add(java.sql.Timestamp.from(to)); }
        sql.append(" ORDER BY a.created_at DESC,a.id DESC LIMIT ?"); args.add(size);
        return jdbc.query(sql.toString(), (rs, row) -> new AuditLogResponse(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), redact(rs.getString(7)),
                redact(rs.getString(8)), rs.getObject(9, UUID.class)), args.toArray());
    }

    public AuditLogResponse get(UUID id) {
        List<AuditLogResponse> rows = jdbc.query("""
                SELECT a.id,a.created_at,COALESCE(u.display_name,'SYSTEM'),a.entity_type,a.entity_id,a.action,
                       a.old_value::text,a.new_value::text,a.correlation_id
                FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id WHERE a.id=?
                """, (rs, row) -> new AuditLogResponse(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), redact(rs.getString(7)),
                redact(rs.getString(8)), rs.getObject(9, UUID.class)), id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "AUDIT_LOG_NOT_FOUND", "Không tìm thấy nhật ký.");
        return rows.getFirst();
    }

    private String redact(String json) { return json == null ? null : SECRET.matcher(json).replaceAll("$1\\\"[REDACTED]\\\""); }
}
