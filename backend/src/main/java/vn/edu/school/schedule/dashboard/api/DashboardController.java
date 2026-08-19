package vn.edu.school.schedule.dashboard.api;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.dashboard.DashboardService;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping("/me") ApiResponse<UserDashboardResponse> me(@RequestParam(required=false) UUID weekId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ApiResponse.success(service.user(weekId, actor), correlation()); }
    @GetMapping("/admin") ApiResponse<AdminDashboardResponse> admin() { return ApiResponse.success(service.admin(), correlation()); }
    private String correlation() { String value=MDC.get(CorrelationIdFilter.MDC_KEY); return value == null ? "unavailable" : value; }
}
