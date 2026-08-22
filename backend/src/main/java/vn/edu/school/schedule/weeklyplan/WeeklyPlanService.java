package vn.edu.school.schedule.weeklyplan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.school.schedule.auth.api.ResourceRef;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.weeklyplan.api.CopyPlanResponse;
import vn.edu.school.schedule.weeklyplan.api.DaySessionResponse;
import vn.edu.school.schedule.weeklyplan.api.DaySessionWrite;
import vn.edu.school.schedule.weeklyplan.api.EventResponse;
import vn.edu.school.schedule.weeklyplan.api.EventWriteRequest;
import vn.edu.school.schedule.weeklyplan.api.PlanIssue;
import vn.edu.school.schedule.weeklyplan.api.PlanValidationResponse;
import vn.edu.school.schedule.weeklyplan.api.PublishPlanRequest;
import vn.edu.school.schedule.weeklyplan.api.PublishedPlanUpdateRequest;
import vn.edu.school.schedule.weeklyplan.api.PlanDayResponse;
import vn.edu.school.schedule.weeklyplan.api.PlanSectionResponse;
import vn.edu.school.schedule.weeklyplan.api.PlanSectionWrite;
import vn.edu.school.schedule.weeklyplan.api.PlanTargetResponse;
import vn.edu.school.schedule.weeklyplan.api.PlanTargetWrite;
import vn.edu.school.schedule.weeklyplan.api.PlanWeekSummary;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanOptions;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanResponse;
import vn.edu.school.schedule.weeklyplan.api.WeeklyPlanUpdateRequest;

@Service
public class WeeklyPlanService {
    private static final List<String> SECTION_TYPES = List.of(
            "ACADEMIC_AFFAIRS", "FACILITIES_OFFICE", "YOUTH_UNION", "HOMEROOM_TEACHERS", "TEACHERS");
    private static final Map<String, String> SECTION_TITLES = Map.of(
            "ACADEMIC_AFFAIRS", "Công tác chuyên môn",
            "FACILITIES_OFFICE", "Cơ sở vật chất – thiết bị – văn phòng",
            "YOUTH_UNION", "Đoàn Thanh niên – Hội LHTN",
            "HOMEROOM_TEACHERS", "Giáo viên chủ nhiệm",
            "TEACHERS", "Giáo viên");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public WeeklyPlanService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<PlanWeekSummary> listWeeks(UUID academicYearId) {
        requireAcademicYear(academicYearId);
        return jdbc.query("""
                SELECT w.id,w.academic_year_id,w.sequence_number,w.display_number,w.week_type,
                       w.start_date,w.end_date,p.status
                FROM school_weeks w LEFT JOIN weekly_plans p ON p.school_week_id=w.id
                WHERE w.academic_year_id=? ORDER BY w.sequence_number
                """, (rs, row) -> new PlanWeekSummary(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getShort(3), displayLabel(rs.getString(5), rs.getShort(4)),
                rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class), rs.getString(8)), academicYearId);
    }

    public List<PlanWeekSummary> listPublishedWeeks() {
        return jdbc.query("""
                SELECT w.id,w.academic_year_id,w.sequence_number,w.display_number,w.week_type,
                       w.start_date,w.end_date,p.status
                FROM school_weeks w JOIN weekly_plans p ON p.school_week_id=w.id
                WHERE p.status='PUBLISHED'
                ORDER BY w.start_date DESC,w.sequence_number DESC
                """, (rs, row) -> new PlanWeekSummary(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getShort(3), displayLabel(rs.getString(5), rs.getShort(4)),
                rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class), rs.getString(8)));
    }

    public WeeklyPlanResponse getByWeek(UUID weekId, AuthenticatedUser actor) {
        WeeklyPlanResponse result = planByWeek(weekId);
        if (!"ADMIN".equals(actor.systemRole()) && !"PUBLISHED".equals(result.status()))
            throw notFound("PLAN_NOT_FOUND");
        return result;
    }

    public WeeklyPlanResponse currentPublished() {
        List<UUID> ids = jdbc.query("""
                SELECT p.id FROM weekly_plans p JOIN school_weeks w ON w.id=p.school_week_id
                WHERE p.status='PUBLISHED'
                ORDER BY CASE WHEN current_date BETWEEN w.start_date AND w.end_date THEN 0 ELSE 1 END,
                         CASE WHEN w.start_date <= current_date THEN 0 ELSE 1 END,
                         abs(w.start_date-current_date),w.start_date DESC LIMIT 1
                """, (rs, row) -> rs.getObject(1, UUID.class));
        if (ids.isEmpty()) throw notFound("PLAN_NOT_FOUND");
        return plan(ids.getFirst());
    }

    public WeeklyPlanResponse currentForAdmin() {
        List<UUID> ids = jdbc.query("""
                SELECT p.id FROM weekly_plans p JOIN school_weeks w ON w.id=p.school_week_id
                ORDER BY CASE WHEN current_date BETWEEN w.start_date AND w.end_date THEN 0 ELSE 1 END,
                         abs(w.start_date-current_date),w.start_date DESC LIMIT 1
                """, (rs, row) -> rs.getObject(1, UUID.class));
        if (ids.isEmpty()) throw notFound("PLAN_NOT_FOUND");
        return plan(ids.getFirst());
    }

    public WeeklyPlanOptions options(UUID planId) {
        WeekInfo week = weekForPlan(planId);
        List<ResourceRef> classes = refs("""
                SELECT id,name FROM school_classes WHERE academic_year_id=? AND is_active=true ORDER BY grade,name
                """, week.academicYearId());
        List<ResourceRef> departments = refs("SELECT id,name FROM departments WHERE is_active=true ORDER BY name");
        List<ResourceRef> roles = refs("SELECT id,name FROM business_roles WHERE is_active=true ORDER BY name");
        List<ResourceRef> users = refs("SELECT id,display_name FROM users WHERE status='ACTIVE' ORDER BY display_name");
        return new WeeklyPlanOptions(classes, departments, roles, users);
    }

    @Transactional
    public WeeklyPlanResponse create(UUID weekId, AuthenticatedUser actor) {
        UUID planId = createPlan(weekId, actor.id());
        WeeklyPlanResponse result = plan(planId);
        audit(actor.id(), "WeeklyPlan", planId, "WEEKLY_PLAN_CREATED", null, snapshot(result));
        return result;
    }

    @Transactional
    public CopyPlanResponse copy(UUID targetWeekId, UUID sourceWeekId, String idempotencyKey,
                                 AuthenticatedUser actor) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200)
            throw validation("IDEMPOTENCY_KEY_REQUIRED", "Copy kế hoạch yêu cầu Idempotency-Key hợp lệ.");
        String cleanKey = idempotencyKey.trim();
        long lock = actor.id().getMostSignificantBits() ^ actor.id().getLeastSignificantBits() ^ cleanKey.hashCode();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + lock + ")");
        List<IdempotentResult> previous = jdbc.query("""
                SELECT k.resource_id,p.school_week_id,k.warnings::text FROM api_idempotency_keys k
                JOIN weekly_plans p ON p.id=k.resource_id
                WHERE k.actor_user_id=? AND k.operation='WEEKLY_PLAN_COPY' AND k.idempotency_key=?
                """, (rs, row) -> new IdempotentResult(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                warningList(rs.getString(3))),
                actor.id(), cleanKey);
        if (!previous.isEmpty()) {
            if (!previous.getFirst().weekId().equals(targetWeekId))
                throw conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key đã được dùng cho tuần khác.");
            return new CopyPlanResponse(plan(previous.getFirst().planId()), previous.getFirst().warnings());
        }

        WeeklyPlanResponse source = planByWeek(sourceWeekId);
        UUID targetPlanId = createPlan(targetWeekId, actor.id());
        jdbc.update("""
                UPDATE plan_sections target SET content=source.content,updated_at=now()
                FROM plan_sections source
                WHERE target.weekly_plan_id=? AND source.weekly_plan_id=?
                  AND target.section_type=source.section_type
                """, targetPlanId, source.id());
        List<TargetCopy> targetCopies = jdbc.query("""
                SELECT target.id,source_target.target_type,source_target.business_role_id,
                       source_target.department_id,source_target.user_id
                FROM plan_section_targets source_target
                JOIN plan_sections source_section ON source_section.id=source_target.plan_section_id
                JOIN plan_sections target ON target.weekly_plan_id=? AND target.section_type=source_section.section_type
                WHERE source_section.weekly_plan_id=?
                """, (rs, row) -> new TargetCopy(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class)),
                targetPlanId, source.id());
        targetCopies.forEach(target -> jdbc.update("""
                INSERT INTO plan_section_targets(id,plan_section_id,target_type,business_role_id,department_id,user_id)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), target.sectionId(), target.type(), target.roleId(),
                target.departmentId(), target.userId()));

        List<SessionValue> sourceSessions = sessionValues(source.id());
        List<SessionValue> targetSessions = sessionValues(targetPlanId);
        int copied = Math.min(sourceSessions.size(), targetSessions.size());
        for (int index = 0; index < copied; index++)
            jdbc.update("UPDATE day_sessions SET base_content=?,updated_at=now() WHERE id=?",
                    sourceSessions.get(index).baseContent(), targetSessions.get(index).id());

        List<String> warnings = new ArrayList<>();
        if (sourceSessions.size() != targetSessions.size()) warnings.add("DAY_COUNT_MISMATCH");
        Integer inactiveTargets = jdbc.queryForObject("""
                SELECT count(*) FROM plan_section_targets t JOIN plan_sections s ON s.id=t.plan_section_id
                WHERE s.weekly_plan_id=? AND (
                  (t.target_type='ROLE' AND NOT EXISTS (SELECT 1 FROM business_roles r WHERE r.id=t.business_role_id AND r.is_active=true)) OR
                  (t.target_type='DEPARTMENT' AND NOT EXISTS (SELECT 1 FROM departments d WHERE d.id=t.department_id AND d.is_active=true)) OR
                  (t.target_type='USER' AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id=t.user_id AND u.status='ACTIVE')))
                """, Integer.class, source.id());
        if (inactiveTargets != null && inactiveTargets > 0) warnings.add("INACTIVE_TARGET_COPIED");
        jdbc.update("""
                INSERT INTO api_idempotency_keys(actor_user_id,operation,idempotency_key,resource_id,warnings)
                VALUES (?,'WEEKLY_PLAN_COPY',?,?,CAST(? AS jsonb))
                """, actor.id(), cleanKey, targetPlanId, jsonString(warnings));
        WeeklyPlanResponse result = plan(targetPlanId);
        audit(actor.id(), "WeeklyPlan", targetPlanId, "WEEKLY_PLAN_COPIED", null,
                Map.of("sourcePlanId", source.id(), "plan", snapshot(result), "warnings", warnings));
        return new CopyPlanResponse(result, warnings);
    }

    @Transactional
    public WeeklyPlanResponse update(UUID planId, WeeklyPlanUpdateRequest request, AuthenticatedUser actor) {
        WeeklyPlanResponse before = plan(planId);
        if (!"DRAFT".equals(before.status())) throw conflict("INVALID_STATE", "Chỉ được sửa DRAFT trong Phase 4.");
        WeekInfo week = weekForPlan(planId);
        validateStructure(request, week);
        validateDutyClass(request.dutyClasses().morningClassId(), week.academicYearId());
        validateDutyClass(request.dutyClasses().afternoonClassId(), week.academicYearId());
        request.sections().forEach(this::validateTargets);

        int changed = jdbc.update("""
                UPDATE weekly_plans SET morning_duty_class_id=?,afternoon_duty_class_id=?,
                    version=version+1,updated_at=now() WHERE id=? AND version=? AND status='DRAFT'
                """, request.dutyClasses().morningClassId(), request.dutyClasses().afternoonClassId(),
                planId, request.version());
        if (changed == 0) throw versionConflict();

        for (PlanSectionWrite section : request.sections()) {
            UUID sectionId = jdbc.queryForObject("""
                    UPDATE plan_sections SET content=?,display_order=?,updated_at=now()
                    WHERE weekly_plan_id=? AND section_type=? RETURNING id
                    """, UUID.class, cleanNullable(section.content()), section.displayOrder(), planId, section.sectionType());
            jdbc.update("DELETE FROM plan_section_targets WHERE plan_section_id=?", sectionId);
            for (PlanTargetWrite target : section.targets()) insertTarget(sectionId, target);
        }
        for (DaySessionWrite session : request.daySessions()) {
            int updated = jdbc.update("""
                    UPDATE day_sessions SET base_content=?,updated_at=now()
                    WHERE weekly_plan_id=? AND session_date=? AND session=?
                    """, cleanNullable(session.baseContent()), planId, session.date(), session.session());
            if (updated != 1) throw validation("PLAN_STRUCTURE_INVALID", "DaySession không khớp cấu trúc tuần.");
        }
        WeeklyPlanResponse result = plan(planId);
        audit(actor.id(), "WeeklyPlan", planId, "WEEKLY_PLAN_UPDATED", snapshot(before), snapshot(result));
        return result;
    }

    @Transactional
    public WeeklyPlanResponse updatePublished(UUID planId, PublishedPlanUpdateRequest published, AuthenticatedUser actor) {
        WeeklyPlanUpdateRequest request = published.plan();
        WeeklyPlanResponse before = plan(planId);
        if (!"PUBLISHED".equals(before.status())) throw conflict("INVALID_STATE", "Plan is not published.");
        WeekInfo week = weekForPlan(planId);
        validateStructure(request, week);
        validateDutyClass(request.dutyClasses().morningClassId(), week.academicYearId());
        validateDutyClass(request.dutyClasses().afternoonClassId(), week.academicYearId());
        request.sections().forEach(this::validateTargets);
        int changed = jdbc.update("""
                UPDATE weekly_plans SET morning_duty_class_id=?,afternoon_duty_class_id=?,
                    version=version+1,updated_at=now() WHERE id=? AND version=? AND status='PUBLISHED'
                """, request.dutyClasses().morningClassId(), request.dutyClasses().afternoonClassId(),
                planId, request.version());
        if (changed != 1) throw versionConflict();
        writeAggregate(planId, request);
        WeeklyPlanResponse result = plan(planId);
        audit(actor.id(), "WeeklyPlan", planId, "WEEKLY_PLAN_PUBLISHED_UPDATED", snapshot(before), snapshot(result));
        if (published.notifyWebsite() || published.notifyEmail())
            enqueuePlanNotification(planId, result.version(), "WEEKLY_PLAN_UPDATED",
                    published.notifyWebsite(), published.notifyEmail());
        return result;
    }

    private void writeAggregate(UUID planId, WeeklyPlanUpdateRequest request) {
        for (PlanSectionWrite section : request.sections()) {
            UUID sectionId = jdbc.queryForObject("""
                    UPDATE plan_sections SET content=?,display_order=?,updated_at=now()
                    WHERE weekly_plan_id=? AND section_type=? RETURNING id
                    """, UUID.class, cleanNullable(section.content()), section.displayOrder(), planId, section.sectionType());
            jdbc.update("DELETE FROM plan_section_targets WHERE plan_section_id=?", sectionId);
            for (PlanTargetWrite target : section.targets()) insertTarget(sectionId, target);
        }
        for (DaySessionWrite session : request.daySessions()) {
            int updated = jdbc.update("""
                    UPDATE day_sessions SET base_content=?,updated_at=now()
                    WHERE weekly_plan_id=? AND session_date=? AND session=?
                    """, cleanNullable(session.baseContent()), planId, session.date(), session.session());
            if (updated != 1) throw validation("PLAN_STRUCTURE_INVALID", "DaySession does not match week structure.");
        }
    }

    public PlanValidationResponse validate(UUID planId) {
        WeeklyPlanResponse value = plan(planId);
        List<PlanIssue> errors = new ArrayList<>();
        List<PlanIssue> warnings = new ArrayList<>();
        if (value.startDate().isAfter(value.endDate()))
            errors.add(new PlanIssue("WEEK_DATE_INVALID", "Week date range is invalid."));
        if (value.sections().size() != 5 || value.days().stream().anyMatch(day -> day.sessions().size() != 2))
            errors.add(new PlanIssue("PLAN_STRUCTURE_INVALID", "Weekly plan structure is incomplete."));
        if (value.morningDutyClass() == null) warnings.add(new PlanIssue("MISSING_MORNING_DUTY", "Chưa chọn lớp trực buổi sáng."));
        if (value.afternoonDutyClass() == null) warnings.add(new PlanIssue("MISSING_AFTERNOON_DUTY", "Chưa chọn lớp trực buổi chiều."));
        value.sections().stream().filter(section -> section.content().isBlank()).forEach(section ->
                warnings.add(new PlanIssue("EMPTY_SECTION", section.title() + " chưa có nội dung.")));
        value.days().stream().filter(day -> day.sessions().stream().allMatch(session ->
                session.baseContent().isBlank() && session.events().isEmpty())).forEach(day ->
                warnings.add(new PlanIssue("EMPTY_DAY", day.dayLabel() + " chưa có nội dung.")));
        jdbc.query("SELECT content FROM events WHERE weekly_plan_id=? AND (start_date IS NULL OR start_time IS NULL)",
                (rs, row) -> new PlanIssue("EVENT_MISSING_TIME", "Sự kiện '" + rs.getString(1) + "' chưa đầy đủ ngày giờ."), planId)
                .forEach(warnings::add);
        return new PlanValidationResponse(errors, warnings);
    }

    @Transactional
    public WeeklyPlanResponse publish(UUID planId, PublishPlanRequest request, String idempotencyKey,
                                      AuthenticatedUser actor) {
        String cleanKey = requireIdempotencyKey(idempotencyKey, "Publish plan");
        long lock = actor.id().getMostSignificantBits() ^ actor.id().getLeastSignificantBits() ^ cleanKey.hashCode();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + lock + ")");
        List<UUID> previous = jdbc.query("""
                SELECT resource_id FROM api_idempotency_keys
                WHERE actor_user_id=? AND operation='WEEKLY_PLAN_PUBLISH' AND idempotency_key=?
                """, (rs, row) -> rs.getObject(1, UUID.class), actor.id(), cleanKey);
        if (!previous.isEmpty()) {
            if (!previous.getFirst().equals(planId)) throw conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used for another plan.");
            return plan(planId);
        }
        WeeklyPlanResponse before = plan(planId);
        if (!"DRAFT".equals(before.status())) throw conflict("INVALID_STATE", "Plan is not a DRAFT.");
        PlanValidationResponse review = validate(planId);
        if (!review.errors().isEmpty()) throw validation("PUBLISH_BLOCKED", "Plan has blocking validation errors.");
        if (!review.warnings().isEmpty() && !request.publishWithWarnings())
            throw validation("PUBLISH_WARNINGS_UNCONFIRMED", "Publish warnings must be confirmed.");
        int changed = jdbc.update("""
                UPDATE weekly_plans SET status='PUBLISHED',published_at=now(),published_by=?,
                    version=version+1,updated_at=now() WHERE id=? AND version=? AND status='DRAFT'
                """, actor.id(), planId, request.version());
        if (changed != 1) throw versionConflict();
        List<String> warningCodes = review.warnings().stream().map(PlanIssue::code).toList();
        jdbc.update("""
                INSERT INTO api_idempotency_keys(actor_user_id,operation,idempotency_key,resource_id,warnings)
                VALUES (?,'WEEKLY_PLAN_PUBLISH',?,?,CAST(? AS jsonb))
                """, actor.id(), cleanKey, planId, jsonString(warningCodes));
        WeeklyPlanResponse result = plan(planId);
        enqueuePlanNotification(planId, result.version(), "WEEKLY_PLAN_PUBLISHED", true, false);
        audit(actor.id(), "WeeklyPlan", planId, "WEEKLY_PLAN_PUBLISHED", snapshot(before), snapshot(result));
        return result;
    }

    @Transactional
    public EventResponse createEvent(UUID planId, EventWriteRequest request, AuthenticatedUser actor) {
        WeeklyPlanResponse parent = plan(planId);
        validateEvent(request);
        requireNotificationChoice(parent.status(), request.notifyWebsite(), request.notifyEmail());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events(id,weekly_plan_id,content,start_date,end_date,session,start_time,end_time,location,note,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, id, planId, request.content().trim(), request.startDate(), request.endDate(), request.session(),
                request.startTime(), request.endTime(), cleanNullable(request.location()), cleanNullable(request.note()), actor.id());
        jdbc.update("UPDATE weekly_plans SET version=version+1,updated_at=now() WHERE id=?", planId);
        EventResponse result = event(id);
        audit(actor.id(), "Event", id, "EVENT_CREATED", null, snapshot(result));
        notifyPublishedMutation(parent, request.notifyWebsite(), request.notifyEmail());
        return result;
    }

    @Transactional
    public EventResponse updateEvent(UUID eventId, EventWriteRequest request, AuthenticatedUser actor) {
        EventResponse before = event(eventId);
        UUID planId = eventPlanId(eventId);
        WeeklyPlanResponse parent = plan(planId);
        validateEvent(request);
        requireNotificationChoice(parent.status(), request.notifyWebsite(), request.notifyEmail());
        if (request.version() == null) throw validation("EVENT_VERSION_REQUIRED", "Event version is required.");
        int changed = jdbc.update("""
                UPDATE events SET content=?,start_date=?,end_date=?,session=?,start_time=?,end_time=?,location=?,note=?,
                    version=version+1,updated_at=now() WHERE id=? AND version=?
                """, request.content().trim(), request.startDate(), request.endDate(), request.session(), request.startTime(),
                request.endTime(), cleanNullable(request.location()), cleanNullable(request.note()), eventId, request.version());
        if (changed != 1) throw versionConflict();
        jdbc.update("UPDATE weekly_plans SET version=version+1,updated_at=now() WHERE id=?", planId);
        EventResponse result = event(eventId);
        audit(actor.id(), "Event", eventId, "EVENT_UPDATED", snapshot(before), snapshot(result));
        notifyPublishedMutation(parent, request.notifyWebsite(), request.notifyEmail());
        return result;
    }

    @Transactional
    public void deleteEvent(UUID eventId, long version, Boolean notifyWebsite, Boolean notifyEmail,
                            AuthenticatedUser actor) {
        EventResponse before = event(eventId);
        UUID planId = eventPlanId(eventId);
        WeeklyPlanResponse parent = plan(planId);
        requireNotificationChoice(parent.status(), notifyWebsite, notifyEmail);
        if (jdbc.update("DELETE FROM events WHERE id=? AND version=?", eventId, version) != 1) throw versionConflict();
        jdbc.update("UPDATE weekly_plans SET version=version+1,updated_at=now() WHERE id=?", planId);
        audit(actor.id(), "Event", eventId, "EVENT_DELETED", snapshot(before), null);
        notifyPublishedMutation(parent, notifyWebsite, notifyEmail);
    }

    private UUID createPlan(UUID weekId, UUID actorId) {
        WeekInfo week = week(weekId);
        UUID planId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO weekly_plans(id,school_week_id,created_by) VALUES (?,?,?)", planId, weekId, actorId);
        } catch (DuplicateKeyException exception) {
            throw conflict("PLAN_EXISTS", "Tuần đã có kế hoạch.");
        }
        for (short index = 0; index < SECTION_TYPES.size(); index++)
            jdbc.update("""
                    INSERT INTO plan_sections(id,weekly_plan_id,section_type,content,display_order)
                    VALUES (?,?,?,?,?)
                    """, UUID.randomUUID(), planId, SECTION_TYPES.get(index), null, index + 1);
        for (LocalDate date = week.startDate(); !date.isAfter(week.endDate()); date = date.plusDays(1)) {
            jdbc.update("INSERT INTO day_sessions(id,weekly_plan_id,session_date,session) VALUES (?,?,?,'MORNING')",
                    UUID.randomUUID(), planId, date);
            jdbc.update("INSERT INTO day_sessions(id,weekly_plan_id,session_date,session) VALUES (?,?,?,'AFTERNOON')",
                    UUID.randomUUID(), planId, date);
        }
        return planId;
    }

    private void validateStructure(WeeklyPlanUpdateRequest request, WeekInfo week) {
        Set<String> types = new HashSet<>();
        Set<Short> orders = new HashSet<>();
        for (PlanSectionWrite section : request.sections()) {
            types.add(section.sectionType()); orders.add(section.displayOrder());
        }
        if (!types.equals(new HashSet<>(SECTION_TYPES)) || !orders.equals(Set.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5)))
            throw validation("PLAN_STRUCTURE_INVALID", "Kế hoạch phải có đúng 5 section/type/order chuẩn.");
        Set<String> expected = new HashSet<>();
        for (LocalDate date = week.startDate(); !date.isAfter(week.endDate()); date = date.plusDays(1)) {
            expected.add(date + "|MORNING"); expected.add(date + "|AFTERNOON");
        }
        Set<String> received = new HashSet<>();
        for (DaySessionWrite item : request.daySessions()) received.add(item.date() + "|" + item.session());
        if (received.size() != request.daySessions().size() || !received.equals(expected))
            throw validation("PLAN_STRUCTURE_INVALID", "Kế hoạch phải có đúng hai buổi cho mỗi ngày trong tuần.");
    }

    private void validateTargets(PlanSectionWrite section) {
        Set<String> unique = new HashSet<>();
        boolean all = false;
        for (PlanTargetWrite target : section.targets()) {
            if ("ALL".equals(target.targetType())) {
                if (target.targetId() != null) throw validation("TARGET_INVALID", "Target ALL không có targetId.");
                all = true;
            } else {
                if (target.targetId() == null) throw validation("TARGET_INVALID", "Target cần targetId.");
                requireActiveTarget(target);
            }
            if (!unique.add(target.targetType() + ":" + target.targetId()))
                throw validation("TARGET_DUPLICATE", "Target bị trùng trong section.");
        }
        if (all && section.targets().size() > 1)
            throw validation("TARGET_INVALID", "Target ALL không kết hợp với target khác.");
    }

    private void requireActiveTarget(PlanTargetWrite target) {
        String sql = switch (target.targetType()) {
            case "ROLE" -> "SELECT count(*) FROM business_roles WHERE id=? AND is_active=true";
            case "DEPARTMENT" -> "SELECT count(*) FROM departments WHERE id=? AND is_active=true";
            case "USER" -> "SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'";
            default -> throw validation("TARGET_INVALID", "Loại target không hợp lệ.");
        };
        Integer count = jdbc.queryForObject(sql, Integer.class, target.targetId());
        if (count == null || count != 1) throw validation("TARGET_INACTIVE", "Target không tồn tại hoặc không hoạt động.");
    }

    private void validateDutyClass(UUID classId, UUID yearId) {
        if (classId == null) return;
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM school_classes WHERE id=? AND academic_year_id=? AND is_active=true
                """, Integer.class, classId, yearId);
        if (count == null || count != 1)
            throw validation("DUTY_CLASS_INVALID", "Lớp trực phải hoạt động và thuộc cùng năm học.");
    }

    private void insertTarget(UUID sectionId, PlanTargetWrite target) {
        UUID role = "ROLE".equals(target.targetType()) ? target.targetId() : null;
        UUID department = "DEPARTMENT".equals(target.targetType()) ? target.targetId() : null;
        UUID user = "USER".equals(target.targetType()) ? target.targetId() : null;
        jdbc.update("""
                INSERT INTO plan_section_targets(id,plan_section_id,target_type,business_role_id,department_id,user_id)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), sectionId, target.targetType(), role, department, user);
    }

    private WeeklyPlanResponse planByWeek(UUID weekId) {
        List<UUID> plans = jdbc.query("SELECT id FROM weekly_plans WHERE school_week_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), weekId);
        if (plans.isEmpty()) throw notFound("PLAN_NOT_FOUND");
        return plan(plans.getFirst());
    }

    private WeeklyPlanResponse plan(UUID planId) {
        List<PlanHeader> headers = jdbc.query("""
                SELECT p.id,w.id,w.sequence_number,w.display_number,w.week_type,w.start_date,w.end_date,
                       p.status,p.version,p.published_at,p.published_by,m.id,m.name,a.id,a.name
                FROM weekly_plans p JOIN school_weeks w ON w.id=p.school_week_id
                LEFT JOIN school_classes m ON m.id=p.morning_duty_class_id
                LEFT JOIN school_classes a ON a.id=p.afternoon_duty_class_id WHERE p.id=?
                """, (rs, row) -> new PlanHeader(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getShort(3), rs.getShort(4), rs.getString(5), rs.getObject(6, LocalDate.class),
                rs.getObject(7, LocalDate.class), rs.getString(8), rs.getLong(9),
                rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(), rs.getObject(11, UUID.class),
                ref(rs.getObject(12, UUID.class), rs.getString(13)), ref(rs.getObject(14, UUID.class), rs.getString(15))), planId);
        if (headers.isEmpty()) throw notFound("PLAN_NOT_FOUND");
        PlanHeader header = headers.getFirst();
        List<PlanSectionResponse> sections = jdbc.query("""
                SELECT id,section_type,content,display_order FROM plan_sections
                WHERE weekly_plan_id=? ORDER BY display_order
                """, (rs, row) -> {
            UUID id = rs.getObject(1, UUID.class);
            String type = rs.getString(2);
            return new PlanSectionResponse(id, type, SECTION_TITLES.get(type), nullToEmpty(rs.getString(3)),
                    rs.getShort(4), targets(id));
        }, planId);
        List<EventResponse> allEvents = jdbc.query("""
                SELECT id,content,start_date,end_date,session,start_time,end_time,location,note,version
                FROM events WHERE weekly_plan_id=? ORDER BY COALESCE(start_date,?),start_time NULLS FIRST,created_at,id
                """, (rs, row) -> eventResponse(rs), planId, header.start());
        Map<LocalDate, List<DaySessionResponse>> byDate = new LinkedHashMap<>();
        List<SessionProjection> sessionRows = jdbc.query("""
                SELECT session_date,session,base_content FROM day_sessions
                WHERE weekly_plan_id=? ORDER BY session_date,CASE session WHEN 'MORNING' THEN 1 ELSE 2 END
                """, (rs, row) -> new SessionProjection(rs.getObject(1, LocalDate.class), rs.getString(2),
                rs.getString(3)), planId);
        sessionRows.forEach(item -> byDate.computeIfAbsent(item.date(), ignored -> new ArrayList<>())
                .add(new DaySessionResponse(item.session(), nullToEmpty(item.baseContent()),
                        eventsForSlot(allEvents, item.date(), item.session(), header.start()))));
        List<PlanDayResponse> days = byDate.entrySet().stream()
                .map(entry -> new PlanDayResponse(entry.getKey(), dayLabel(entry.getKey()), entry.getValue())).toList();
        return new WeeklyPlanResponse(header.id(), header.weekId(), header.sequence(),
                displayLabel(header.weekType(), header.display()), header.start(), header.end(), header.status(),
                header.version(), header.publishedAt(), header.publishedBy(),
                header.morning(), header.afternoon(), sections, days);
    }

    private List<PlanTargetResponse> targets(UUID sectionId) {
        return jdbc.query("""
                SELECT t.target_type,COALESCE(t.business_role_id,t.department_id,t.user_id),
                       COALESCE(r.name,d.name,u.display_name,'Tất cả')
                FROM plan_section_targets t
                LEFT JOIN business_roles r ON r.id=t.business_role_id
                LEFT JOIN departments d ON d.id=t.department_id
                LEFT JOIN users u ON u.id=t.user_id
                WHERE t.plan_section_id=? ORDER BY t.target_type,3
                """, (rs, row) -> new PlanTargetResponse(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3)), sectionId);
    }

    private List<SessionValue> sessionValues(UUID planId) {
        return jdbc.query("""
                SELECT id,base_content FROM day_sessions WHERE weekly_plan_id=?
                ORDER BY session_date,CASE session WHEN 'MORNING' THEN 1 ELSE 2 END
                """, (rs, row) -> new SessionValue(rs.getObject(1, UUID.class), rs.getString(2)), planId);
    }

    private WeekInfo week(UUID weekId) {
        List<WeekInfo> rows = jdbc.query("""
                SELECT id,academic_year_id,start_date,end_date FROM school_weeks WHERE id=?
                """, (rs, row) -> new WeekInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class)), weekId);
        if (rows.isEmpty()) throw notFound("SCHOOL_WEEK_NOT_FOUND");
        return rows.getFirst();
    }

    private WeekInfo weekForPlan(UUID planId) {
        List<WeekInfo> rows = jdbc.query("""
                SELECT w.id,w.academic_year_id,w.start_date,w.end_date FROM school_weeks w
                JOIN weekly_plans p ON p.school_week_id=w.id WHERE p.id=?
                """, (rs, row) -> new WeekInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class)), planId);
        if (rows.isEmpty()) throw notFound("PLAN_NOT_FOUND");
        return rows.getFirst();
    }

    private void requireAcademicYear(UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM academic_years WHERE id=?", Integer.class, id);
        if (count == null || count != 1) throw notFound("ACADEMIC_YEAR_NOT_FOUND");
    }

    private List<ResourceRef> refs(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new ResourceRef(rs.getObject(1, UUID.class), rs.getString(2)), args);
    }
    private EventResponse event(UUID eventId) {
        List<EventResponse> rows = jdbc.query("""
                SELECT id,content,start_date,end_date,session,start_time,end_time,location,note,version
                FROM events WHERE id=?
                """, (rs, row) -> eventResponse(rs), eventId);
        if (rows.isEmpty()) throw notFound("EVENT_NOT_FOUND");
        return rows.getFirst();
    }
    private UUID eventPlanId(UUID eventId) {
        List<UUID> rows = jdbc.query("SELECT weekly_plan_id FROM events WHERE id=?",
                (rs, row) -> rs.getObject(1, UUID.class), eventId);
        if (rows.isEmpty()) throw notFound("EVENT_NOT_FOUND");
        return rows.getFirst();
    }
    private EventResponse eventResponse(ResultSet rs) throws SQLException {
        return new EventResponse(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), rs.getString(5),
                rs.getObject(6, LocalTime.class), rs.getObject(7, LocalTime.class),
                rs.getString(8), rs.getString(9), rs.getLong(10));
    }
    private List<EventResponse> eventsForSlot(List<EventResponse> events, LocalDate date, String session,
                                              LocalDate planStart) {
        return events.stream().filter(event -> {
            LocalDate start = event.startDate() == null ? planStart : event.startDate();
            LocalDate end = event.endDate() == null ? start : event.endDate();
            return !date.isBefore(start) && !date.isAfter(end)
                    && (event.session() == null || event.session().equals(session));
        }).toList();
    }
    private void validateEvent(EventWriteRequest request) {
        if (request.endDate() != null && (request.startDate() == null || request.startDate().isAfter(request.endDate())))
            throw validation("EVENT_DATE_INVALID", "Event date range is invalid.");
        if (request.endTime() != null && (request.startTime() == null || request.startTime().isAfter(request.endTime())))
            throw validation("EVENT_TIME_INVALID", "Event time range is invalid.");
    }
    private void requireNotificationChoice(String status, Boolean website, Boolean email) {
        if ("PUBLISHED".equals(status) && (website == null || email == null))
            throw validation("NOTIFICATION_CHOICE_REQUIRED", "Published changes require explicit notification choices.");
    }
    private void notifyPublishedMutation(WeeklyPlanResponse parent, Boolean website, Boolean email) {
        if (!"PUBLISHED".equals(parent.status()) || (!Boolean.TRUE.equals(website) && !Boolean.TRUE.equals(email))) return;
        Long version = jdbc.queryForObject("SELECT version FROM weekly_plans WHERE id=?", Long.class, parent.id());
        enqueuePlanNotification(parent.id(), version == null ? parent.version() : version,
                "WEEKLY_PLAN_UPDATED", Boolean.TRUE.equals(website), Boolean.TRUE.equals(email));
    }
    private void enqueuePlanNotification(UUID planId, long version, String eventType,
                                         boolean website, boolean email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("weeklyPlanId", planId);
        payload.put("version", version);
        payload.put("notifyWebsite", website);
        payload.put("notifyEmail", email);
        jdbc.update("""
                INSERT INTO outbox_messages(id,event_type,aggregate_type,aggregate_id,deduplication_key,payload)
                VALUES (?,?, 'WeeklyPlan',?,?,CAST(? AS jsonb)) ON CONFLICT (deduplication_key) DO NOTHING
                """, UUID.randomUUID(), eventType, planId, eventType + ":" + planId + ":" + version, jsonString(payload));
    }
    private String requireIdempotencyKey(String value, String operation) {
        if (value == null || value.isBlank() || value.length() > 200)
            throw validation("IDEMPOTENCY_KEY_REQUIRED", operation + " requires a valid Idempotency-Key.");
        return value.trim();
    }
    private ResourceRef ref(UUID id, String name) { return id == null ? null : new ResourceRef(id, name); }
    private String displayLabel(String type, short display) {
        return "ORIENTATION".equals(type) ? "Tuần định hướng " + display : "Tuần " + display;
    }
    private String dayLabel(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Thứ Hai"; case TUESDAY -> "Thứ Ba"; case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm"; case FRIDAY -> "Thứ Sáu"; case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
    }
    private String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private String jsonString(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Cannot serialize JSON", exception); }
    }
    private List<String> warningList(String value) {
        try { return json.readValue(value, new tools.jackson.core.type.TypeReference<List<String>>() { }); }
        catch (JacksonException exception) { throw new IllegalStateException("Cannot parse idempotency warnings", exception); }
    }

    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException validation(String code, String message) { return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message); }
    private ApiException notFound(String code) { return new ApiException(HttpStatus.NOT_FOUND, code, "Không tìm thấy dữ liệu."); }
    private ApiException versionConflict() { return conflict("VERSION_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại."); }
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
        } catch (JacksonException exception) { throw new IllegalStateException("Cannot serialize audit snapshot", exception); }
    }

    private record WeekInfo(UUID id, UUID academicYearId, LocalDate startDate, LocalDate endDate) { }
    private record PlanHeader(UUID id, UUID weekId, short sequence, short display, String weekType,
                              LocalDate start, LocalDate end, String status, long version,
                              Instant publishedAt, UUID publishedBy,
                              ResourceRef morning, ResourceRef afternoon) { }
    private record SessionValue(UUID id, String baseContent) { }
    private record SessionProjection(LocalDate date, String session, String baseContent) { }
    private record TargetCopy(UUID sectionId, String type, UUID roleId, UUID departmentId, UUID userId) { }
    private record IdempotentResult(UUID planId, UUID weekId, List<String> warnings) { }
}
