package vn.edu.school.schedule.reminder;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.school.schedule.reminder.api.ReminderResponse;
import vn.edu.school.schedule.reminder.api.ReminderWriteRequest;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@Service
public class ReminderService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId schoolZone;

    public ReminderService(JdbcTemplate jdbc, Clock clock, @Value("${app.school-zone}") String schoolZone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.schoolZone = ZoneId.of(schoolZone);
    }

    @Transactional
    public List<ReminderResponse> create(UUID eventId, ReminderWriteRequest request, AuthenticatedUser actor) {
        EventInfo event = event(eventId, actor);
        Instant remindAt = calculateRemindAt(event, request);
        if (!remindAt.isAfter(clock.instant())) {
            throw validation("REMINDER_NOT_FUTURE", "Thời điểm nhắc phải ở tương lai.");
        }
        boolean admin = "ADMIN".equals(actor.systemRole());
        List<UUID> recipients = recipients(request.recipientUserIds(), actor, admin);
        List<ReminderResponse> result = new ArrayList<>();
        for (UUID recipient : recipients) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO reminders(id,event_id,owner_user_id,source,created_by,remind_at)
                    VALUES (?,?,?,?,?,?)
                    """, id, eventId, recipient, admin ? "ADMIN" : "USER", actor.id(), Timestamp.from(remindAt));
            audit(actor.id(), id, "REMINDER_CREATED");
            result.add(get(id));
        }
        return result;
    }

    public List<ReminderResponse> listMine(AuthenticatedUser actor, String status) {
        if (status != null && !List.of("PENDING", "PROCESSING", "SENT", "FAILED", "CANCELLED").contains(status)) {
            throw validation("REMINDER_STATUS_INVALID", "Trạng thái reminder không hợp lệ.");
        }
        String filter = status == null ? "" : " AND r.status=?";
        Object[] args = status == null ? new Object[]{actor.id()} : new Object[]{actor.id(), status};
        return jdbc.query("""
                SELECT r.id,r.event_id,e.content,r.owner_user_id,r.source,r.remind_at,r.status,r.attempt_count,r.sent_at
                FROM reminders r JOIN events e ON e.id=r.event_id
                WHERE r.owner_user_id=? %s ORDER BY r.remind_at,r.id LIMIT 200
                """.formatted(filter), this::map, args);
    }

    @Transactional
    public void cancel(UUID id, AuthenticatedUser actor) {
        List<Ownership> rows = jdbc.query("SELECT owner_user_id,source,status FROM reminders WHERE id=?",
                (rs, row) -> new Ownership(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), id);
        if (rows.isEmpty()) throw notFound();
        Ownership value = rows.getFirst();
        boolean admin = "ADMIN".equals(actor.systemRole());
        if (!admin && (!value.owner().equals(actor.id()) || "ADMIN".equals(value.source()))) throw notFound();
        if (!"PENDING".equals(value.status())) {
            if ("CANCELLED".equals(value.status())) return;
            throw new ApiException(HttpStatus.CONFLICT, "REMINDER_NOT_CANCELLABLE", "Reminder không còn ở trạng thái chờ.");
        }
        jdbc.update("UPDATE reminders SET status='CANCELLED',updated_at=now() WHERE id=?", id);
        audit(actor.id(), id, "REMINDER_CANCELLED");
    }

    private EventInfo event(UUID id, AuthenticatedUser actor) {
        String visibility = "USER".equals(actor.systemRole()) ? " AND p.status='PUBLISHED'" : "";
        List<EventInfo> rows = jdbc.query("""
                SELECT e.start_date,e.start_time,e.content FROM events e
                JOIN weekly_plans p ON p.id=e.weekly_plan_id WHERE e.id=? %s
                """.formatted(visibility), (rs, row) -> new EventInfo(
                rs.getObject(1, LocalDate.class), rs.getObject(2, LocalTime.class), rs.getString(3)), id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện.");
        return rows.getFirst();
    }

    private Instant calculateRemindAt(EventInfo event, ReminderWriteRequest request) {
        String preset = request.preset();
        boolean custom = preset == null || "CUSTOM".equals(preset);
        if (custom) {
            if (request.remindAt() == null) throw validation("REMIND_AT_REQUIRED", "Cần nhập thời điểm nhắc cụ thể.");
            return request.remindAt();
        }
        if (request.remindAt() != null) throw validation("REMINDER_TIME_AMBIGUOUS", "Chỉ chọn preset hoặc thời điểm cụ thể.");
        if (event.date() == null || event.time() == null) throw validation("EVENT_DATETIME_REQUIRED", "Sự kiện chưa có đủ ngày giờ để dùng preset.");
        Duration offset = switch (preset) {
            case "MINUTES_15" -> Duration.ofMinutes(15);
            case "MINUTES_30" -> Duration.ofMinutes(30);
            case "HOUR_1" -> Duration.ofHours(1);
            case "DAY_1" -> Duration.ofDays(1);
            default -> throw validation("REMINDER_PRESET_INVALID", "Preset reminder không hợp lệ.");
        };
        return event.date().atTime(event.time()).atZone(schoolZone).toInstant().minus(offset);
    }

    private List<UUID> recipients(List<UUID> requested, AuthenticatedUser actor, boolean admin) {
        if (!admin) {
            if (requested != null && (requested.size() != 1 || !requested.getFirst().equals(actor.id()))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "REMINDER_RECIPIENT_FORBIDDEN", "User chỉ được tạo reminder cho chính mình.");
            }
            return List.of(actor.id());
        }
        if (requested == null || requested.isEmpty()) {
            return jdbc.query("SELECT id FROM users WHERE status='ACTIVE' AND system_role='USER' ORDER BY id",
                    (rs, row) -> rs.getObject(1, UUID.class));
        }
        List<UUID> distinct = requested.stream().distinct().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        Integer valid = jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (" + placeholders + ") AND status='ACTIVE'",
                Integer.class, distinct.toArray());
        if (valid == null || valid != distinct.size()) {
            throw validation("REMINDER_RECIPIENT_INVALID", "Recipient phải là tài khoản đang hoạt động.");
        }
        return distinct;
    }

    private ReminderResponse get(UUID id) {
        return jdbc.query("""
                SELECT r.id,r.event_id,e.content,r.owner_user_id,r.source,r.remind_at,r.status,r.attempt_count,r.sent_at
                FROM reminders r JOIN events e ON e.id=r.event_id WHERE r.id=?
                """, this::map, id).getFirst();
    }

    private ReminderResponse map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp sent = rs.getTimestamp(9);
        return new ReminderResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getString(7),
                rs.getInt(8), sent == null ? null : sent.toInstant());
    }

    private void audit(UUID actor, UUID id, String action) {
        UUID correlation;
        try { correlation = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)); }
        catch (Exception ignored) { correlation = UUID.randomUUID(); }
        jdbc.update("""
                INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,new_value,correlation_id)
                VALUES (?,?,'USER','Reminder',?,?,jsonb_build_object('status',?),?)
                """, UUID.randomUUID(), actor, id, action, action.endsWith("CANCELLED") ? "CANCELLED" : "PENDING", correlation);
    }

    private ApiException validation(String code, String message) { return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "REMINDER_NOT_FOUND", "Không tìm thấy reminder."); }
    private record EventInfo(LocalDate date, LocalTime time, String title) { }
    private record Ownership(UUID owner, String source, String status) { }
}
