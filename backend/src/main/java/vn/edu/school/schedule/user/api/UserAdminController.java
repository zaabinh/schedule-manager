package vn.edu.school.schedule.user.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.auth.api.CurrentUser;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.user.UserAdminService;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {
    private final UserAdminService users;
    public UserAdminController(UserAdminService users) { this.users = users; }

    @GetMapping
    ApiResponse<List<CurrentUser>> list(@RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        PageResponse<CurrentUser> result = users.list(status, page, size);
        return ApiResponse.page(result.items(), result.page(), result.size(), result.total(), correlationId());
    }

    @GetMapping("/approval-options")
    ApiResponse<ApprovalOptions> approvalOptions() {
        return ApiResponse.success(users.approvalOptions(), correlationId());
    }

    @PatchMapping("/{id}/approval")
    ApiResponse<CurrentUser> approve(@PathVariable UUID id, @Valid @RequestBody ApprovalRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(users.approve(id, request, actor), correlationId());
    }

    @PatchMapping("/{id}")
    ApiResponse<CurrentUser> update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(users.update(id, request, actor), correlationId());
    }

    @PatchMapping("/{id}/status")
    ApiResponse<CurrentUser> status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(users.setStatus(id, request, actor), correlationId());
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
