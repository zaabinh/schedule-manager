package vn.edu.school.schedule.organization;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.school.schedule.auth.api.ResourceRef;
import vn.edu.school.schedule.organization.api.BusinessRoleResponse;
import vn.edu.school.schedule.organization.api.CreateResourceRequest;
import vn.edu.school.schedule.organization.api.CreateSchoolClassRequest;
import vn.edu.school.schedule.organization.api.DepartmentResponse;
import vn.edu.school.schedule.organization.api.OrganizationOptions;
import vn.edu.school.schedule.organization.api.SchoolClassResponse;
import vn.edu.school.schedule.organization.api.UpdateResourceRequest;
import vn.edu.school.schedule.organization.api.UpdateSchoolClassRequest;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@Service
public class OrganizationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OrganizationService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PageResult<DepartmentResponse> departments(Boolean active, int page, int size) {
        validatePage(page, size);
        String where = active == null ? "" : " WHERE is_active=?";
        Object[] filter = active == null ? new Object[]{} : new Object[]{active};
        Long total = jdbc.queryForObject("SELECT count(*) FROM departments" + where, Long.class, filter);
        List<Object> args = new ArrayList<>();
        args.addAll(java.util.Arrays.asList(filter));
        args.add(size); args.add(page * size);
        List<DepartmentResponse> items = jdbc.query("""
                SELECT id,name,description,is_active,version FROM departments
                """ + where + " ORDER BY name LIMIT ? OFFSET ?", (rs, row) -> new DepartmentResponse(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getLong(5)),
                args.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public PageResult<BusinessRoleResponse> roles(Boolean active, int page, int size) {
        validatePage(page, size);
        String where = active == null ? "" : " WHERE is_active=?";
        Object[] filter = active == null ? new Object[]{} : new Object[]{active};
        Long total = jdbc.queryForObject("SELECT count(*) FROM business_roles" + where, Long.class, filter);
        List<Object> args = new ArrayList<>();
        args.addAll(java.util.Arrays.asList(filter));
        args.add(size); args.add(page * size);
        List<BusinessRoleResponse> items = jdbc.query("""
                SELECT id,name,description,is_protected,is_active,version FROM business_roles
                """ + where + " ORDER BY is_protected DESC,name LIMIT ? OFFSET ?", (rs, row) -> new BusinessRoleResponse(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getBoolean(5), rs.getLong(6)), args.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public PageResult<SchoolClassResponse> classes(UUID academicYearId, Boolean active, int page, int size) {
        validatePage(page, size);
        List<String> predicates = new ArrayList<>();
        List<Object> filter = new ArrayList<>();
        if (academicYearId != null) { predicates.add("c.academic_year_id=?"); filter.add(academicYearId); }
        if (active != null) { predicates.add("c.is_active=?"); filter.add(active); }
        String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        Long total = jdbc.queryForObject("SELECT count(*) FROM school_classes c" + where, Long.class, filter.toArray());
        List<Object> args = new ArrayList<>(filter);
        args.add(size); args.add(page * size);
        List<SchoolClassResponse> items = jdbc.query("""
                SELECT c.id,c.name,c.grade,c.is_active,c.version,
                       y.id,y.name,u.id,u.display_name
                FROM school_classes c JOIN academic_years y ON y.id=c.academic_year_id
                LEFT JOIN users u ON u.id=c.homeroom_teacher_id
                """ + where + " ORDER BY y.start_date DESC,c.grade,c.name LIMIT ? OFFSET ?", this::mapClass, args.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public OrganizationOptions options() {
        List<ResourceRef> years = refs("SELECT id,name FROM academic_years WHERE is_active=true ORDER BY start_date DESC");
        List<ResourceRef> teachers = refs("""
                SELECT u.id,u.display_name FROM users u
                WHERE u.system_role='USER' AND u.status='ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM school_classes c WHERE c.homeroom_teacher_id=u.id AND c.is_active=true)
                ORDER BY u.display_name
                """);
        return new OrganizationOptions(years, teachers);
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateResourceRequest request, AuthenticatedUser actor) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO departments(id,name,normalized_name,description) VALUES (?,?,?,?)",
                    id, clean(request.name()), normalize(request.name()), cleanNullable(request.description()));
        } catch (DuplicateKeyException exception) { throw duplicate("DEPARTMENT_NAME_EXISTS"); }
        DepartmentResponse result = department(id);
        audit(actor.id(), "Department", id, "DEPARTMENT_CREATED", null, snapshot(result));
        return result;
    }

    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateResourceRequest request, AuthenticatedUser actor) {
        DepartmentResponse before = department(id);
        try {
            int changed = jdbc.update("""
                    UPDATE departments SET name=?,normalized_name=?,description=?,is_active=?,version=version+1,updated_at=now()
                    WHERE id=? AND version=?
                    """, clean(request.name()), normalize(request.name()), cleanNullable(request.description()),
                    request.isActive(), id, request.version());
            if (changed == 0) throw versionConflict();
        } catch (DuplicateKeyException exception) { throw duplicate("DEPARTMENT_NAME_EXISTS"); }
        DepartmentResponse result = department(id);
        audit(actor.id(), "Department", id, "DEPARTMENT_UPDATED", snapshot(before), snapshot(result));
        return result;
    }

    @Transactional
    public BusinessRoleResponse createRole(CreateResourceRequest request, AuthenticatedUser actor) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO business_roles(id,name,normalized_name,description) VALUES (?,?,?,?)",
                    id, clean(request.name()), normalize(request.name()), cleanNullable(request.description()));
        } catch (DuplicateKeyException exception) { throw duplicate("BUSINESS_ROLE_NAME_EXISTS"); }
        BusinessRoleResponse result = role(id);
        audit(actor.id(), "BusinessRole", id, "BUSINESS_ROLE_CREATED", null, snapshot(result));
        return result;
    }

    @Transactional
    public BusinessRoleResponse updateRole(UUID id, UpdateResourceRequest request, AuthenticatedUser actor) {
        BusinessRoleResponse before = role(id);
        if (before.isProtected())
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_PROTECTED", "Vai trò mặc định được bảo vệ.");
        try {
            int changed = jdbc.update("""
                    UPDATE business_roles SET name=?,normalized_name=?,description=?,is_active=?,version=version+1,updated_at=now()
                    WHERE id=? AND version=?
                    """, clean(request.name()), normalize(request.name()), cleanNullable(request.description()),
                    request.isActive(), id, request.version());
            if (changed == 0) throw versionConflict();
        } catch (DuplicateKeyException exception) { throw duplicate("BUSINESS_ROLE_NAME_EXISTS"); }
        BusinessRoleResponse result = role(id);
        audit(actor.id(), "BusinessRole", id, "BUSINESS_ROLE_UPDATED", snapshot(before), snapshot(result));
        return result;
    }

    @Transactional
    public SchoolClassResponse createClass(CreateSchoolClassRequest request, AuthenticatedUser actor) {
        requireActiveYear(request.academicYearId());
        requireTeacherAvailable(request.homeroomTeacherId(), null);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO school_classes(id,academic_year_id,name,normalized_name,grade,homeroom_teacher_id)
                    VALUES (?,?,?,?,?,?)
                    """, id, request.academicYearId(), clean(request.name()), normalize(request.name()),
                    request.grade(), request.homeroomTeacherId());
        } catch (DuplicateKeyException exception) { throw classConflict(exception); }
        SchoolClassResponse result = schoolClass(id);
        audit(actor.id(), "SchoolClass", id, "SCHOOL_CLASS_CREATED", null, snapshot(result));
        return result;
    }

    @Transactional
    public SchoolClassResponse updateClass(UUID id, UpdateSchoolClassRequest request, AuthenticatedUser actor) {
        SchoolClassResponse before = schoolClass(id);
        requireActiveYear(request.academicYearId());
        if (request.isActive()) requireTeacherAvailable(request.homeroomTeacherId(), id);
        try {
            int changed = jdbc.update("""
                    UPDATE school_classes SET academic_year_id=?,name=?,normalized_name=?,grade=?,homeroom_teacher_id=?,
                        is_active=?,version=version+1,updated_at=now()
                    WHERE id=? AND version=?
                    """, request.academicYearId(), clean(request.name()), normalize(request.name()), request.grade(),
                    request.homeroomTeacherId(), request.isActive(), id, request.version());
            if (changed == 0) throw versionConflict();
        } catch (DuplicateKeyException exception) { throw classConflict(exception); }
        SchoolClassResponse result = schoolClass(id);
        audit(actor.id(), "SchoolClass", id, "SCHOOL_CLASS_UPDATED", snapshot(before), snapshot(result));
        return result;
    }

    private DepartmentResponse department(UUID id) {
        List<DepartmentResponse> rows = jdbc.query("SELECT id,name,description,is_active,version FROM departments WHERE id=?",
                (rs, row) -> new DepartmentResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getLong(5)), id);
        if (rows.isEmpty()) throw notFound("DEPARTMENT_NOT_FOUND");
        return rows.getFirst();
    }

    private BusinessRoleResponse role(UUID id) {
        List<BusinessRoleResponse> rows = jdbc.query("SELECT id,name,description,is_protected,is_active,version FROM business_roles WHERE id=?",
                (rs, row) -> new BusinessRoleResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getBoolean(5), rs.getLong(6)), id);
        if (rows.isEmpty()) throw notFound("BUSINESS_ROLE_NOT_FOUND");
        return rows.getFirst();
    }

    private SchoolClassResponse schoolClass(UUID id) {
        List<SchoolClassResponse> rows = jdbc.query("""
                SELECT c.id,c.name,c.grade,c.is_active,c.version,y.id,y.name,u.id,u.display_name
                FROM school_classes c JOIN academic_years y ON y.id=c.academic_year_id
                LEFT JOIN users u ON u.id=c.homeroom_teacher_id WHERE c.id=?
                """, this::mapClass, id);
        if (rows.isEmpty()) throw notFound("SCHOOL_CLASS_NOT_FOUND");
        return rows.getFirst();
    }

    private SchoolClassResponse mapClass(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        ResourceRef year = new ResourceRef(rs.getObject(6, UUID.class), rs.getString(7));
        UUID teacherId = rs.getObject(8, UUID.class);
        ResourceRef teacher = teacherId == null ? null : new ResourceRef(teacherId, rs.getString(9));
        return new SchoolClassResponse(rs.getObject(1, UUID.class), year, rs.getString(2), rs.getShort(3),
                teacher, rs.getBoolean(4), rs.getLong(5));
    }

    private void requireActiveYear(UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_years WHERE id=? AND is_active=true", Integer.class, id);
        if (count == null || count != 1)
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ACADEMIC_YEAR_INACTIVE", "Năm học không tồn tại hoặc đã ngừng hoạt động.");
    }

    private void requireTeacherAvailable(UUID teacherId, UUID currentClass) {
        if (teacherId == null) return;
        Integer active = jdbc.queryForObject("SELECT count(*) FROM users WHERE id=? AND system_role='USER' AND status='ACTIVE'",
                Integer.class, teacherId);
        if (active == null || active != 1)
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "HOMEROOM_TEACHER_INACTIVE", "Giáo viên chủ nhiệm phải là USER đang hoạt động.");
        Integer assigned = currentClass == null
                ? jdbc.queryForObject("SELECT count(*) FROM school_classes WHERE homeroom_teacher_id=? AND is_active=true", Integer.class, teacherId)
                : jdbc.queryForObject("SELECT count(*) FROM school_classes WHERE homeroom_teacher_id=? AND is_active=true AND id<>?", Integer.class, teacherId, currentClass);
        if (assigned != null && assigned > 0)
            throw new ApiException(HttpStatus.CONFLICT, "HOMEROOM_CONFLICT", "Giáo viên đã chủ nhiệm một lớp đang hoạt động.");
    }

    private List<ResourceRef> refs(String sql) {
        return jdbc.query(sql, (rs, row) -> new ResourceRef(rs.getObject(1, UUID.class), rs.getString(2)));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Phân trang không hợp lệ.");
    }

    private String clean(String value) { return value.trim().replaceAll("\\s+", " "); }
    private String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private ApiException duplicate(String code) {
        return new ApiException(HttpStatus.CONFLICT, code, "Tên đã tồn tại.");
    }
    private ApiException notFound(String code) {
        return new ApiException(HttpStatus.NOT_FOUND, code, "Không tìm thấy dữ liệu.");
    }
    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại.");
    }
    private ApiException classConflict(DuplicateKeyException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains("ux_active_homeroom_teacher")
                ? new ApiException(HttpStatus.CONFLICT, "HOMEROOM_CONFLICT", "Giáo viên đã chủ nhiệm một lớp đang hoạt động.")
                : duplicate("SCHOOL_CLASS_NAME_EXISTS");
    }

    private Map<String, Object> snapshot(Object value) {
        return json.convertValue(value, new tools.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private void audit(UUID actor, String entityType, UUID entityId, String action,
                       Map<String, Object> before, Map<String, Object> after) {
        UUID correlation;
        try { correlation = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)); }
        catch (Exception ignored) { correlation = UUID.randomUUID(); }
        try {
            jdbc.update("""
                    INSERT INTO audit_logs(id,actor_user_id,actor_type,entity_type,entity_id,action,old_value,new_value,correlation_id)
                    VALUES (?,?,'USER',?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)
                    """, UUID.randomUUID(), actor, entityType, entityId, action,
                    before == null ? null : json.writeValueAsString(before),
                    after == null ? null : json.writeValueAsString(after), correlation);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize audit snapshot", exception);
        }
    }
}
