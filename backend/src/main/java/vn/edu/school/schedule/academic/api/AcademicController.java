package vn.edu.school.schedule.academic.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.academic.AcademicService;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1")
public class AcademicController {
    private final AcademicService academic;
    public AcademicController(AcademicService academic) { this.academic = academic; }

    @GetMapping("/academic-years")
    ApiResponse<List<AcademicYearResponse>> years(@RequestParam(required = false) Boolean active) {
        return ApiResponse.success(academic.years(active), correlationId());
    }

    @PostMapping("/academic-years") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AcademicYearResponse> create(@Valid @RequestBody CreateAcademicYearRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(academic.create(request, actor), correlationId());
    }

    @PatchMapping("/academic-years/{id}")
    ApiResponse<AcademicYearResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateAcademicYearRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(academic.update(id, request, actor), correlationId());
    }

    @PostMapping("/academic-years/{id}/weeks/generate") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<List<SchoolWeekResponse>> generate(@PathVariable UUID id,
            @Valid @RequestBody GenerateWeeksRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(academic.generate(id, request.count(), actor), correlationId());
    }

    @GetMapping("/academic-years/{id}/weeks")
    ApiResponse<List<SchoolWeekResponse>> weeks(@PathVariable UUID id) {
        return ApiResponse.success(academic.weeks(id), correlationId());
    }

    @PatchMapping("/weeks/{id}")
    ApiResponse<SchoolWeekResponse> updateWeek(@PathVariable UUID id,
            @Valid @RequestBody UpdateSchoolWeekRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(academic.updateWeek(id, request, actor), correlationId());
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
