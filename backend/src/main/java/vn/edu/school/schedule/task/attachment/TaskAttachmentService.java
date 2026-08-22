package vn.edu.school.schedule.task.attachment;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.task.storage.FileStorage;
import vn.edu.school.schedule.task.storage.FileStorageException;

@Service
public class TaskAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(TaskAttachmentService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final FileStorage storage;
    private final TaskAttachmentFileValidator validator;
    private final TaskAttachmentProperties limits;
    private final ObjectMapper json;

    public TaskAttachmentService(JdbcTemplate jdbc, TransactionTemplate transaction, FileStorage storage,
                                 TaskAttachmentFileValidator validator, TaskAttachmentProperties limits,
                                 ObjectMapper json) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.storage = storage;
        this.validator = validator;
        this.limits = limits;
        this.json = json;
    }

    public TaskAttachmentResponse upload(UUID taskId, MultipartFile file, AuthenticatedUser actor) {
        requireAdmin(actor);
        requireTask(taskId);
        var valid = validator.validate(file, limits.maxFileSizeBytes());
        UUID attachmentId = UUID.randomUUID();
        String storageKey = "tasks/" + taskId + "/" + attachmentId + "." + valid.extension();
        FileStorage.StoredFile stored;
        try {
            stored = storage.upload(storageKey, file.getInputStream());
        } catch (IOException | FileStorageException exception) {
            throw storageUnavailable("Không thể tải tệp lên kho lưu trữ.", exception);
        }
        try {
            if (stored.fileSize() != file.getSize() || stored.fileSize() > limits.maxFileSizeBytes()) {
                throw validation("FILE_SIZE_INVALID", "Kích thước tệp lưu trữ không hợp lệ.");
            }
            return transaction.execute(status -> {
                lockTask(taskId);
                requireCapacity(taskId, stored.fileSize());
                jdbc.update("""
                        INSERT INTO task_attachments(id,task_id,uploaded_by,original_name,storage_key,content_type,file_size,checksum)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, attachmentId, taskId, actor.id(), valid.originalName(), stored.storageKey(),
                        valid.contentType(), stored.fileSize(), stored.checksum());
                TaskAttachmentResponse result = getMetadata(attachmentId);
                audit(actor.id(), attachmentId, "TASK_ATTACHMENT_ADDED", null, auditValue(result));
                return result;
            });
        } catch (RuntimeException exception) {
            cleanupAfterMetadataFailure(stored.storageKey());
            throw exception;
        }
    }

    public List<TaskAttachmentResponse> list(UUID taskId, AuthenticatedUser actor) {
        UUID assignee = requireTask(taskId);
        authorizeRead(actor, assignee);
        return jdbc.query("""
                SELECT id,task_id,original_name,content_type,file_size,checksum,created_at
                FROM task_attachments WHERE task_id=? AND deleted_at IS NULL ORDER BY created_at,id
                """, this::map, taskId);
    }

    public TaskAttachmentDownload download(UUID attachmentId, AuthenticatedUser actor) {
        AttachmentRow row = attachment(attachmentId, false);
        authorizeRead(actor, row.assigneeId());
        try {
            return new TaskAttachmentDownload(storage.download(row.storageKey()), row.response().originalName(),
                    row.response().contentType(), row.response().fileSize());
        } catch (FileStorageException exception) {
            throw storageUnavailable("Không thể tải tệp từ kho lưu trữ.", exception);
        }
    }

    public void delete(UUID attachmentId, AuthenticatedUser actor) {
        requireAdmin(actor);
        transaction.executeWithoutResult(status -> {
            AttachmentRow row = attachment(attachmentId, true);
            try {
                storage.delete(row.storageKey());
            } catch (FileStorageException exception) {
                throw storageUnavailable("Không thể xóa tệp khỏi kho lưu trữ.", exception);
            }
            int changed = jdbc.update("UPDATE task_attachments SET deleted_at=now() WHERE id=? AND deleted_at IS NULL", attachmentId);
            if (changed != 1) throw notFoundAttachment();
            Map<String, Object> after = new LinkedHashMap<>(auditValue(row.response()));
            after.put("deleted", true);
            audit(actor.id(), attachmentId, "TASK_ATTACHMENT_REMOVED", auditValue(row.response()), after);
        });
    }

    private UUID requireTask(UUID taskId) {
        List<UUID> rows = jdbc.query("SELECT assignee_user_id FROM tasks WHERE id=?",
                (rs, row) -> rs.getObject(1, UUID.class), taskId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Không tìm thấy nhiệm vụ.");
        return rows.getFirst();
    }

    private void lockTask(UUID taskId) {
        List<UUID> rows = jdbc.query("SELECT id FROM tasks WHERE id=? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), taskId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Không tìm thấy nhiệm vụ.");
    }

    private void requireCapacity(UUID taskId, long newSize) {
        Map<String, Object> aggregate = jdbc.queryForMap("""
                SELECT count(*) file_count,COALESCE(sum(file_size),0) total_size
                FROM task_attachments WHERE task_id=? AND deleted_at IS NULL
                """, taskId);
        long count = ((Number) aggregate.get("file_count")).longValue();
        long total = ((Number) aggregate.get("total_size")).longValue();
        if (count >= limits.maxFiles()) throw validation("ATTACHMENT_COUNT_EXCEEDED", "Nhiệm vụ đã đạt số tệp tối đa.");
        if (total + newSize > limits.maxTotalSizeBytes()) {
            throw validation("ATTACHMENT_TOTAL_SIZE_EXCEEDED", "Tổng dung lượng tệp của nhiệm vụ vượt quá giới hạn.");
        }
    }

    private AttachmentRow attachment(UUID id, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<AttachmentRow> rows = jdbc.query("""
                SELECT a.id,a.task_id,a.original_name,a.content_type,a.file_size,a.checksum,a.created_at,
                       a.storage_key,t.assignee_user_id
                FROM task_attachments a JOIN tasks t ON t.id=a.task_id
                WHERE a.id=? AND a.deleted_at IS NULL
                """ + suffix, (rs, row) -> new AttachmentRow(map(rs, row), rs.getString(8),
                rs.getObject(9, UUID.class)), id);
        if (rows.isEmpty()) throw notFoundAttachment();
        return rows.getFirst();
    }

    private TaskAttachmentResponse getMetadata(UUID id) {
        return jdbc.queryForObject("""
                SELECT id,task_id,original_name,content_type,file_size,checksum,created_at
                FROM task_attachments WHERE id=? AND deleted_at IS NULL
                """, this::map, id);
    }

    private TaskAttachmentResponse map(ResultSet rs, int row) throws SQLException {
        return new TaskAttachmentResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
                rs.getTimestamp(7).toInstant());
    }

    private void authorizeRead(AuthenticatedUser actor, UUID assigneeId) {
        if (actor == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Bạn chưa đăng nhập.");
        if ("ADMIN".equals(actor.systemRole()) || ("USER".equals(actor.systemRole()) && actor.id().equals(assigneeId))) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ATTACHMENT_FORBIDDEN", "Bạn không có quyền truy cập tệp đính kèm này.");
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (actor == null || !"ADMIN".equals(actor.systemRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ATTACHMENT_FORBIDDEN", "Chỉ Admin được thay đổi tệp đính kèm.");
        }
    }

    private Map<String, Object> auditValue(TaskAttachmentResponse value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attachmentId", value.id());
        result.put("taskId", value.taskId());
        result.put("originalName", value.originalName());
        result.put("fileSize", value.fileSize());
        result.put("contentType", value.contentType());
        result.put("checksum", value.checksum());
        return result;
    }

    private void audit(UUID actor, UUID id, String action, Object before, Object after) {
        UUID correlation;
        try { correlation = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)); }
        catch (Exception ignored) { correlation = UUID.randomUUID(); }
        jdbc.update("""
                INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,old_value,new_value,correlation_id)
                VALUES (?,?,'USER','TaskAttachment',?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """, UUID.randomUUID(), actor, id, action, before == null ? null : stringify(before),
                after == null ? null : stringify(after), correlation);
    }

    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Không thể tạo audit payload.", exception); }
    }

    private void cleanupAfterMetadataFailure(String storageKey) {
        try { storage.delete(storageKey); }
        catch (RuntimeException cleanupError) {
            log.error("Task attachment orphan cleanup failed for generated storage key", cleanupError);
        }
    }

    private ApiException validation(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
    private ApiException notFoundAttachment() {
        return new ApiException(HttpStatus.NOT_FOUND, "TASK_ATTACHMENT_NOT_FOUND", "Không tìm thấy tệp đính kèm.");
    }
    private ApiException storageUnavailable(String message, Exception cause) {
        log.warn("Task attachment storage operation failed", cause);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_UNAVAILABLE", message);
    }

    private record AttachmentRow(TaskAttachmentResponse response, String storageKey, UUID assigneeId) { }
}
