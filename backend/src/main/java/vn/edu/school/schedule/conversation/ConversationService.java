package vn.edu.school.schedule.conversation;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.conversation.api.CloseConversationRequest;
import vn.edu.school.schedule.conversation.api.ConversationMessageResponse;
import vn.edu.school.schedule.conversation.api.ConversationResponse;
import vn.edu.school.schedule.conversation.api.ConversationWriteRequest;
import vn.edu.school.schedule.conversation.api.MessageWriteRequest;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@Service
public class ConversationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ConversationService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public ConversationResponse create(ConversationWriteRequest request, AuthenticatedUser actor) {
        String subject = required(request.subject(), 255, "CONVERSATION_SUBJECT_INVALID", "Chủ đề phải có từ 1 đến 255 ký tự.");
        String message = required(request.message(), 10_000, "MESSAGE_INVALID", "Nội dung phải có từ 1 đến 10000 ký tự.");
        String category = clean(request.category(), 100, "CATEGORY_TOO_LONG");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversations(id,created_by,subject,category) VALUES (?,?,?,?)", id, actor.id(), subject, category);
        insertMessage(id, actor.id(), message);
        audit(actor.id(), id, "CONVERSATION_CREATED");
        enqueue(id, actor.id(), "CONVERSATION_OPENED", 0);
        return get(id, actor);
    }

    public List<ConversationResponse> list(AuthenticatedUser actor, String status) {
        validateStatus(status);
        String ownership = "ADMIN".equals(actor.systemRole()) ? "" : " AND c.created_by=?";
        String state = status == null ? "" : " AND c.status=?";
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (!"ADMIN".equals(actor.systemRole())) args.add(actor.id());
        if (status != null) args.add(status);
        List<UUID> ids = jdbc.query("SELECT c.id FROM conversations c WHERE 1=1" + ownership + state + " ORDER BY c.updated_at DESC,c.id DESC LIMIT 100",
                (rs, row) -> rs.getObject(1, UUID.class), args.toArray());
        return ids.stream().map(id -> get(id, actor)).toList();
    }

    public ConversationResponse get(UUID id, AuthenticatedUser actor) {
        String ownership = "ADMIN".equals(actor.systemRole()) ? "" : " AND created_by=?";
        Object[] args = "ADMIN".equals(actor.systemRole()) ? new Object[]{id} : new Object[]{id, actor.id()};
        List<ConversationRow> rows = jdbc.query("""
                SELECT id,subject,category,status,created_by,updated_at,version FROM conversations
                WHERE id=? %s
                """.formatted(ownership), (rs, row) -> new ConversationRow(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getTimestamp(6), rs.getLong(7)), args);
        if (rows.isEmpty()) throw notFound();
        ConversationRow value = rows.getFirst();
        return new ConversationResponse(value.id(), value.subject(), value.category(), value.status(), value.creator(),
                value.updatedAt().toInstant(), value.version(), messages(id));
    }

    @Transactional
    public ConversationResponse send(UUID id, MessageWriteRequest request, AuthenticatedUser actor) {
        ConversationResponse before = get(id, actor);
        if ("CLOSED".equals(before.status())) throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED", "Trao đổi đã đóng.");
        String content = required(request.content(), 10_000, "MESSAGE_INVALID", "Nội dung phải có từ 1 đến 10000 ký tự.");
        insertMessage(id, actor.id(), content);
        int changed = jdbc.update("UPDATE conversations SET version=version+1,updated_at=now() WHERE id=? AND status='OPEN'", id);
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED", "Trao đổi đã đóng.");
        ConversationResponse result = get(id, actor);
        audit(actor.id(), id, "CONVERSATION_MESSAGE_SENT");
        enqueue(id, counterpart(before, actor), "CONVERSATION_MESSAGE", result.version());
        return result;
    }

    @Transactional
    public ConversationResponse close(UUID id, CloseConversationRequest request, AuthenticatedUser actor) {
        ConversationResponse before = get(id, actor);
        if ("CLOSED".equals(before.status())) return before;
        int changed = jdbc.update("""
                UPDATE conversations SET status='CLOSED',closed_at=now(),closed_by=?,version=version+1,updated_at=now()
                WHERE id=? AND version=? AND status='OPEN'
                """, actor.id(), id, request.version());
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Trao đổi đã thay đổi. Vui lòng tải lại.");
        ConversationResponse result = get(id, actor);
        audit(actor.id(), id, "CONVERSATION_CLOSED");
        enqueue(id, result.createdBy(), "CONVERSATION_CLOSED", result.version());
        return result;
    }

    private void insertMessage(UUID conversation, UUID sender, String content) {
        jdbc.update("INSERT INTO conversation_messages(id,conversation_id,sender_id,content) VALUES (?,?,?,?)",
                UUID.randomUUID(), conversation, sender, content);
    }

    private List<ConversationMessageResponse> messages(UUID id) {
        return jdbc.query("""
                SELECT m.id,m.sender_id,u.display_name,m.content,m.created_at FROM conversation_messages m
                JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=? ORDER BY m.created_at,m.id
                """, (rs, row) -> new ConversationMessageResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()), id);
    }

    private UUID counterpart(ConversationResponse conversation, AuthenticatedUser actor) {
        if (!actor.id().equals(conversation.createdBy())) return conversation.createdBy();
        List<UUID> admins = jdbc.query("SELECT id FROM users WHERE system_role='ADMIN' AND status='ACTIVE'", (rs, row) -> rs.getObject(1, UUID.class));
        if (admins.isEmpty()) throw new IllegalStateException("Active Admin is required");
        return admins.getFirst();
    }

    private void enqueue(UUID conversation, UUID recipient, String type, long version) {
        String payload = string(java.util.Map.of("recipientUserId", recipient, "notifyWebsite", true, "notifyEmail", false));
        jdbc.update("""
                INSERT INTO outbox_messages(id,event_type,aggregate_type,aggregate_id,deduplication_key,payload)
                VALUES (?,?,'Conversation',?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), type, conversation, type + ":" + conversation + ":" + version, payload);
    }

    private void audit(UUID actor, UUID id, String action) {
        UUID correlation;
        try { correlation = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)); }
        catch (Exception ignored) { correlation = UUID.randomUUID(); }
        jdbc.update("""
                INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,correlation_id)
                VALUES (?,?,'USER','Conversation',?,?,?)
                """, UUID.randomUUID(), actor, id, action, correlation);
    }

    private String required(String value, int max, String code, String message) {
        String cleaned = value == null ? null : value.trim();
        if (cleaned == null || cleaned.isEmpty() || cleaned.length() > max) throw validation(code, message);
        return cleaned;
    }
    private String clean(String value, int max, String code) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (cleaned.length() > max) throw validation(code, "Giá trị quá dài.");
        return cleaned;
    }
    private void validateStatus(String status) { if (status != null && !List.of("OPEN", "CLOSED").contains(status)) throw validation("CONVERSATION_STATUS_INVALID", "Trạng thái không hợp lệ."); }
    private String string(Object value) { try { return json.writeValueAsString(value); } catch (JacksonException failure) { throw new IllegalStateException(failure); } }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Không tìm thấy trao đổi."); }
    private ApiException validation(String code, String message) { return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message); }
    private record ConversationRow(UUID id, String subject, String category, String status, UUID creator, Timestamp updatedAt, long version) { }
}
