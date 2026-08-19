package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.weeklyplan.WeeklyPlanService;

@RestController
@RequestMapping("/api/v1")
public class WeeklyPlanController {
    private final WeeklyPlanService plans;
    public WeeklyPlanController(WeeklyPlanService plans) { this.plans = plans; }

    @GetMapping("/weekly-plans")
    ApiResponse<List<PlanWeekSummary>> list(@RequestParam UUID academicYearId) {
        return ApiResponse.success(plans.listWeeks(academicYearId), correlationId());
    }

    @GetMapping("/weekly-plans/current")
    ApiResponse<WeeklyPlanResponse> current() {
        return ApiResponse.success(plans.currentPublished(), correlationId());
    }

    @GetMapping("/weeks/{weekId}/plan")
    ApiResponse<WeeklyPlanResponse> get(@PathVariable UUID weekId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.getByWeek(weekId, actor), correlationId());
    }

    @PostMapping("/weeks/{weekId}/plan") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<WeeklyPlanResponse> create(@PathVariable UUID weekId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.create(weekId, actor), correlationId());
    }

    @PostMapping("/weeks/{weekId}/plan/copy") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CopyPlanResponse> copy(@PathVariable UUID weekId,
            @Valid @RequestBody CopyPlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.copy(weekId, request.sourceWeekId(), idempotencyKey, actor), correlationId());
    }

    @GetMapping("/weekly-plans/{id}/options")
    ApiResponse<WeeklyPlanOptions> options(@PathVariable UUID id) {
        return ApiResponse.success(plans.options(id), correlationId());
    }

    @PatchMapping("/weekly-plans/{id}")
    ApiResponse<WeeklyPlanResponse> update(@PathVariable UUID id,
            @Valid @RequestBody WeeklyPlanUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.update(id, request, actor), correlationId());
    }

    @GetMapping("/weekly-plans/{id}/validation")
    ApiResponse<PlanValidationResponse> validate(@PathVariable UUID id) {
        return ApiResponse.success(plans.validate(id), correlationId());
    }

    @PostMapping("/weekly-plans/{id}/publish")
    ApiResponse<WeeklyPlanResponse> publish(@PathVariable UUID id,
            @Valid @RequestBody PublishPlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.publish(id, request, idempotencyKey, actor), correlationId());
    }

    @PatchMapping("/weekly-plans/{id}/published-content")
    ApiResponse<WeeklyPlanResponse> updatePublished(@PathVariable UUID id,
            @Valid @RequestBody PublishedPlanUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.updatePublished(id, request, actor), correlationId());
    }

    @PostMapping("/weekly-plans/{id}/events") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<EventResponse> createEvent(@PathVariable UUID id,
            @Valid @RequestBody EventWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.createEvent(id, request, actor), correlationId());
    }

    @PatchMapping("/events/{id}")
    ApiResponse<EventResponse> updateEvent(@PathVariable UUID id,
            @Valid @RequestBody EventWriteRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(plans.updateEvent(id, request, actor), correlationId());
    }

    @DeleteMapping("/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEvent(@PathVariable UUID id, @RequestParam long version,
            @RequestParam(required = false) Boolean notifyWebsite,
            @RequestParam(required = false) Boolean notifyEmail,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        plans.deleteEvent(id, version, notifyWebsite, notifyEmail, actor);
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
