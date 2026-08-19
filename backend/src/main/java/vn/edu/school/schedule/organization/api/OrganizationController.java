package vn.edu.school.schedule.organization.api;

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
import vn.edu.school.schedule.organization.OrganizationService;
import vn.edu.school.schedule.organization.PageResult;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {
    private final OrganizationService organization;
    public OrganizationController(OrganizationService organization) { this.organization = organization; }

    @GetMapping("/departments")
    ApiResponse<List<DepartmentResponse>> departments(@RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        PageResult<DepartmentResponse> result = organization.departments(active, page, size);
        return ApiResponse.page(result.items(), result.page(), result.size(), result.total(), correlationId());
    }

    @PostMapping("/departments") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<DepartmentResponse> createDepartment(@Valid @RequestBody CreateResourceRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(organization.createDepartment(request, actor), correlationId());
    }

    @PatchMapping("/departments/{id}")
    ApiResponse<DepartmentResponse> updateDepartment(@PathVariable UUID id,
            @Valid @RequestBody UpdateResourceRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(organization.updateDepartment(id, request, actor), correlationId());
    }

    @GetMapping("/business-roles")
    ApiResponse<List<BusinessRoleResponse>> roles(@RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        PageResult<BusinessRoleResponse> result = organization.roles(active, page, size);
        return ApiResponse.page(result.items(), result.page(), result.size(), result.total(), correlationId());
    }

    @PostMapping("/business-roles") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<BusinessRoleResponse> createRole(@Valid @RequestBody CreateResourceRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(organization.createRole(request, actor), correlationId());
    }

    @PatchMapping("/business-roles/{id}")
    ApiResponse<BusinessRoleResponse> updateRole(@PathVariable UUID id,
            @Valid @RequestBody UpdateResourceRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(organization.updateRole(id, request, actor), correlationId());
    }

    @GetMapping("/classes")
    ApiResponse<List<SchoolClassResponse>> classes(@RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        PageResult<SchoolClassResponse> result = organization.classes(academicYearId, active, page, size);
        return ApiResponse.page(result.items(), result.page(), result.size(), result.total(), correlationId());
    }

    @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SchoolClassResponse> createClass(@Valid @RequestBody CreateSchoolClassRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(organization.createClass(request, actor), correlationId());
    }

    @PatchMapping("/classes/{id}")
    ApiResponse<SchoolClassResponse> updateClass(@PathVariable UUID id,
            @Valid @RequestBody UpdateSchoolClassRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(organization.updateClass(id, request, actor), correlationId());
    }

    @GetMapping("/organization/options")
    ApiResponse<OrganizationOptions> options() {
        return ApiResponse.success(organization.options(), correlationId());
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
