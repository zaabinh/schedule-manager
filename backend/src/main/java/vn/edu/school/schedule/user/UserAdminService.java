package vn.edu.school.schedule.user;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.school.schedule.auth.AuthService;
import vn.edu.school.schedule.auth.api.CurrentUser;
import vn.edu.school.schedule.auth.api.ResourceRef;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.user.api.ApprovalOptions;
import vn.edu.school.schedule.user.api.ApprovalRequest;
import vn.edu.school.schedule.user.api.PageResponse;
import vn.edu.school.schedule.user.api.StatusRequest;

@Service
public class UserAdminService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuthService auth;

    public UserAdminService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.auth = auth;
    }

    public PageResponse<CurrentUser> list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("page", "Phân trang không hợp lệ.");
        if (status != null && !Set.of("PENDING", "ACTIVE", "INACTIVE").contains(status))
            throw invalid("status", "Trạng thái không hợp lệ.");
        String where = status == null ? "" : " WHERE status=?";
        Object[] args = status == null ? new Object[]{} : new Object[]{status};
        Long total = jdbc.queryForObject("SELECT count(*) FROM users" + where, Long.class, args);
        String sql = "SELECT id FROM users" + where + " ORDER BY created_at DESC,id LIMIT ? OFFSET ?";
        Object[] pageArgs = status == null ? new Object[]{size, page * size} : new Object[]{status, size, page * size};
        List<CurrentUser> items = jdbc.query(sql, (rs, rowNum) -> auth.loadCurrentUser(rs.getObject(1, UUID.class)), pageArgs);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public ApprovalOptions approvalOptions() {
        List<ResourceRef> departments = refs("SELECT id,name FROM departments WHERE is_active=true ORDER BY name");
        List<ResourceRef> roles = refs("SELECT id,name FROM business_roles WHERE is_active=true ORDER BY name");
        List<ResourceRef> classes = refs("SELECT id,name FROM school_classes WHERE is_active=true AND homeroom_teacher_id IS NULL ORDER BY name");
        return new ApprovalOptions(departments, roles, classes);
    }

    @Transactional
    public CurrentUser approve(UUID userId, ApprovalRequest request, AuthenticatedUser actor) {
        UserState before = state(userId);
        if (!"PENDING".equals(before.status()) || !"USER".equals(before.systemRole()))
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Chỉ tài khoản USER đang chờ duyệt mới được phê duyệt.");
        requireActive("departments", request.departmentId(), "DEPARTMENT_INACTIVE");
        requireActiveRoles(request.businessRoleIds());
        if (request.homeroomClassId() != null) requireAvailableClass(request.homeroomClassId());
        int changed = jdbc.update("""
                UPDATE users SET department_id=?,status='ACTIVE',approved_by=?,approved_at=now(),version=version+1,updated_at=now()
                WHERE id=? AND version=? AND status='PENDING'
                """, request.departmentId(), actor.id(), userId, request.version());
        if (changed == 0) throw conflict();
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
        request.businessRoleIds().forEach(role -> jdbc.update(
                "INSERT INTO user_roles(user_id,business_role_id) VALUES (?,?)", userId, role));
        if (request.homeroomClassId() != null) {
            int assigned = jdbc.update("UPDATE school_classes SET homeroom_teacher_id=?,version=version+1,updated_at=now() "
                    + "WHERE id=? AND is_active=true AND homeroom_teacher_id IS NULL", userId, request.homeroomClassId());
            if (assigned == 0) throw new ApiException(HttpStatus.CONFLICT, "USER_CONFIG_CONFLICT", "Lớp đã có giáo viên chủ nhiệm.");
        }
        audit(actor.id(), userId, "USER_APPROVED", before.status(), "ACTIVE");
        return auth.loadCurrentUser(userId);
    }

    @Transactional
    public CurrentUser setStatus(UUID userId, StatusRequest request, AuthenticatedUser actor) {
        UserState before = state(userId);
        if ("ADMIN".equals(before.systemRole()))
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_PROTECTED", "Không thay đổi trạng thái Admin qua endpoint này.");
        if ("ACTIVE".equals(request.status()) && before.departmentId() == null)
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "DEPARTMENT_REQUIRED", "Tài khoản hoạt động phải có phòng ban.");
        int changed = jdbc.update("UPDATE users SET status=?,version=version+1,updated_at=now() WHERE id=? AND version=?",
                request.status(), userId, request.version());
        if (changed == 0) throw conflict();
        if ("INACTIVE".equals(request.status()))
            jdbc.update("UPDATE auth_sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL", userId);
        audit(actor.id(), userId, "USER_STATUS_CHANGED", before.status(), request.status());
        return auth.loadCurrentUser(userId);
    }

    private List<ResourceRef> refs(String sql) {
        return jdbc.query(sql, (rs, rowNum) -> new ResourceRef(rs.getObject(1, UUID.class), rs.getString(2)));
    }

    private UserState state(UUID id) {
        List<UserState> rows = jdbc.query("SELECT system_role,status,department_id FROM users WHERE id=?",
                (rs, rowNum) -> new UserState(rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class)), id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy người dùng.");
        return rows.getFirst();
    }

    private void requireActive(String table, UUID id, String code) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id=? AND is_active=true", Integer.class, id);
        if (count == null || count != 1) throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, "Tham chiếu không hoạt động.");
    }

    private void requireActiveRoles(Set<UUID> roleIds) {
        Map<String, Object> parameters = Map.of("ids", roleIds);
        Integer count = namedJdbc.queryForObject(
                "SELECT count(*) FROM business_roles WHERE id IN (:ids) AND is_active=true", parameters, Integer.class);
        if (count == null || count != roleIds.size())
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_ROLE_INACTIVE", "Vai trò không hợp lệ hoặc đã ngừng hoạt động.");
    }

    private void requireAvailableClass(UUID classId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM school_classes WHERE id=? AND is_active=true AND homeroom_teacher_id IS NULL",
                Integer.class, classId);
        if (count == null || count != 1)
            throw new ApiException(HttpStatus.CONFLICT, "USER_CONFIG_CONFLICT", "Lớp không khả dụng để phân công chủ nhiệm.");
    }

    private void audit(UUID actor, UUID target, String action, String oldStatus, String newStatus) {
        UUID correlation;
        try { correlation = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)); }
        catch (Exception ignored) { correlation = UUID.randomUUID(); }
        jdbc.update("""
                INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,old_value,new_value,correlation_id)
                VALUES (?,?,'USER','User',?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                """, UUID.randomUUID(), actor, target, action,
                "{\"status\":\"" + oldStatus + "\"}", "{\"status\":\"" + newStatus + "\"}", correlation);
    }

    private ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", message,
                List.of(new vn.edu.school.schedule.shared.api.ApiFieldError(field, message)));
    }
    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại.");
    }
    private record UserState(String systemRole, String status, UUID departmentId) { }
}
