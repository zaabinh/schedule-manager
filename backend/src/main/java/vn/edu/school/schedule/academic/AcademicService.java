package vn.edu.school.schedule.academic;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.academic.api.AcademicYearResponse;
import vn.edu.school.schedule.academic.api.CreateAcademicYearRequest;
import vn.edu.school.schedule.academic.api.SchoolWeekResponse;
import vn.edu.school.schedule.academic.api.UpdateAcademicYearRequest;
import vn.edu.school.schedule.academic.api.UpdateSchoolWeekRequest;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@Service
public class AcademicService {
    private static final int DEFAULT_WEEK_COUNT = 39;
    private static final long ACTIVE_YEAR_LOCK = 73032026L;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AcademicService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<AcademicYearResponse> years(Boolean active) {
        String where = active == null ? "" : " WHERE y.is_active=?";
        Object[] args = active == null ? new Object[]{} : new Object[]{active};
        return jdbc.query("""
                SELECT y.id,y.name,y.start_date,y.is_active,y.version,count(w.id)
                FROM academic_years y LEFT JOIN school_weeks w ON w.academic_year_id=y.id
                """ + where + " GROUP BY y.id ORDER BY y.start_date DESC,y.name", this::mapYear, args);
    }

    public List<SchoolWeekResponse> weeks(UUID yearId) {
        requireYear(yearId);
        return jdbc.query("""
                SELECT w.id,w.academic_year_id,w.sequence_number,w.display_number,w.week_type,
                       w.start_date,w.end_date,w.version,
                       EXISTS(SELECT 1 FROM school_weeks other WHERE other.academic_year_id=w.academic_year_id
                         AND other.id<>w.id AND other.start_date<=w.end_date AND other.end_date>=w.start_date)
                FROM school_weeks w WHERE w.academic_year_id=? ORDER BY w.sequence_number
                """, this::mapWeek, yearId);
    }

    @Transactional
    public AcademicYearResponse create(CreateAcademicYearRequest request, AuthenticatedUser actor) {
        String name = validateYearName(request.name());
        boolean active = request.isActive() == null || request.isActive();
        if (active) requireOnlyActive(null);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO academic_years(id,name,start_date,is_active,created_by) VALUES (?,?,?,?,?)",
                    id, name, request.startDate(), active, actor.id());
        } catch (DuplicateKeyException exception) {
            throw conflict("ACADEMIC_YEAR_NAME_EXISTS", "Tên năm học đã tồn tại.");
        }
        AcademicYearResponse created = year(id);
        audit(actor.id(), "AcademicYear", id, "ACADEMIC_YEAR_CREATED", null, snapshot(created));
        if (Boolean.TRUE.equals(request.generateWeeks())) generate(id, DEFAULT_WEEK_COUNT, actor);
        return year(id);
    }

    @Transactional
    public AcademicYearResponse update(UUID id, UpdateAcademicYearRequest request, AuthenticatedUser actor) {
        AcademicYearResponse before = year(id);
        String name = validateYearName(request.name());
        if (request.isActive()) requireOnlyActive(id);
        if (!request.isActive() && before.isActive()) {
            Integer classes = jdbc.queryForObject(
                    "SELECT count(*) FROM school_classes WHERE academic_year_id=? AND is_active=true", Integer.class, id);
            if (classes != null && classes > 0)
                throw conflict("ACADEMIC_YEAR_IN_USE", "Hãy vô hiệu hóa các lớp đang hoạt động trước khi đóng năm học.");
        }
        try {
            int changed = jdbc.update("""
                    UPDATE academic_years SET name=?,start_date=?,is_active=?,version=version+1,updated_at=now()
                    WHERE id=? AND version=?
                    """, name, request.startDate(), request.isActive(), id, request.version());
            if (changed == 0) throw versionConflict();
        } catch (DuplicateKeyException exception) {
            throw conflict("ACADEMIC_YEAR_NAME_EXISTS", "Tên năm học đã tồn tại.");
        }
        AcademicYearResponse result = year(id);
        audit(actor.id(), "AcademicYear", id, "ACADEMIC_YEAR_UPDATED", snapshot(before), snapshot(result));
        return result;
    }

    @Transactional
    public List<SchoolWeekResponse> generate(UUID yearId, int count, AuthenticatedUser actor) {
        AcademicYearResponse target = year(yearId);
        if (count != DEFAULT_WEEK_COUNT)
            throw validation("WEEK_COUNT_INVALID", "MVP yêu cầu sinh đúng 39 tuần.");
        if (target.weekCount() > 0) throw conflict("WEEKS_EXIST", "Năm học đã có tuần.");
        for (short sequence = 1; sequence <= DEFAULT_WEEK_COUNT; sequence++) {
            boolean orientation = sequence <= 2;
            short display = orientation ? sequence : (short) (sequence - 2);
            LocalDate start = target.startDate().plusDays((long) (sequence - 1) * 7);
            jdbc.update("""
                    INSERT INTO school_weeks(id,academic_year_id,sequence_number,display_number,week_type,start_date,end_date)
                    VALUES (?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), yearId, sequence, display, orientation ? "ORIENTATION" : "STUDY",
                    start, start.plusDays(6));
        }
        List<SchoolWeekResponse> result = weeks(yearId);
        audit(actor.id(), "AcademicYear", yearId, "SCHOOL_WEEKS_GENERATED", snapshot(target),
                Map.of("count", result.size()));
        return result;
    }

    @Transactional
    public SchoolWeekResponse updateWeek(UUID id, UpdateSchoolWeekRequest request, AuthenticatedUser actor) {
        if (request.startDate().isAfter(request.endDate()))
            throw validation("WEEK_DATE_INVALID", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        SchoolWeekResponse before = week(id);
        int changed = jdbc.update("""
                UPDATE school_weeks SET display_number=?,week_type=?,start_date=?,end_date=?,
                    version=version+1,updated_at=now() WHERE id=? AND version=?
                """, request.displayNumber(), request.weekType(), request.startDate(), request.endDate(),
                id, request.version());
        if (changed == 0) throw versionConflict();
        SchoolWeekResponse result = week(id);
        audit(actor.id(), "SchoolWeek", id, "SCHOOL_WEEK_UPDATED", snapshot(before), snapshot(result));
        return result;
    }

    private void requireOnlyActive(UUID currentId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + ACTIVE_YEAR_LOCK + ")");
        Integer count = currentId == null
                ? jdbc.queryForObject("SELECT count(*) FROM academic_years WHERE is_active=true", Integer.class)
                : jdbc.queryForObject("SELECT count(*) FROM academic_years WHERE is_active=true AND id<>?", Integer.class, currentId);
        if (count != null && count > 0)
            throw conflict("ACTIVE_ACADEMIC_YEAR_EXISTS", "Chỉ được có một năm học đang hoạt động.");
    }

    private String validateYearName(String value) {
        String name = value.trim().replaceAll("\\s+", " ");
        if (!name.matches("\\d{4}-\\d{4}"))
            throw validation("ACADEMIC_YEAR_NAME_INVALID", "Tên năm học phải có dạng YYYY-YYYY.");
        int first = Integer.parseInt(name.substring(0, 4));
        int second = Integer.parseInt(name.substring(5));
        if (second != first + 1)
            throw validation("ACADEMIC_YEAR_NAME_INVALID", "Hai năm trong tên năm học phải liên tiếp.");
        return name;
    }

    private AcademicYearResponse requireYear(UUID id) { return year(id); }

    private AcademicYearResponse year(UUID id) {
        List<AcademicYearResponse> rows = jdbc.query("""
                SELECT y.id,y.name,y.start_date,y.is_active,y.version,count(w.id)
                FROM academic_years y LEFT JOIN school_weeks w ON w.academic_year_id=y.id
                WHERE y.id=? GROUP BY y.id
                """, this::mapYear, id);
        if (rows.isEmpty()) throw notFound("ACADEMIC_YEAR_NOT_FOUND");
        return rows.getFirst();
    }

    private SchoolWeekResponse week(UUID id) {
        List<SchoolWeekResponse> rows = jdbc.query("""
                SELECT w.id,w.academic_year_id,w.sequence_number,w.display_number,w.week_type,
                       w.start_date,w.end_date,w.version,
                       EXISTS(SELECT 1 FROM school_weeks other WHERE other.academic_year_id=w.academic_year_id
                         AND other.id<>w.id AND other.start_date<=w.end_date AND other.end_date>=w.start_date)
                FROM school_weeks w WHERE w.id=?
                """, this::mapWeek, id);
        if (rows.isEmpty()) throw notFound("SCHOOL_WEEK_NOT_FOUND");
        return rows.getFirst();
    }

    private AcademicYearResponse mapYear(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AcademicYearResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, LocalDate.class),
                rs.getBoolean(4), rs.getLong(5), rs.getInt(6));
    }

    private SchoolWeekResponse mapWeek(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        List<String> warnings = rs.getBoolean(9) ? List.of("WEEK_OVERLAP") : List.of();
        return new SchoolWeekResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getShort(3),
                rs.getShort(4), rs.getString(5), rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class),
                rs.getLong(8), warnings);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    private ApiException validation(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
    private ApiException notFound(String code) {
        return new ApiException(HttpStatus.NOT_FOUND, code, "Không tìm thấy dữ liệu.");
    }
    private ApiException versionConflict() {
        return conflict("VERSION_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại.");
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
