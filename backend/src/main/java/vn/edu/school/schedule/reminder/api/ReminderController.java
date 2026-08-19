package vn.edu.school.schedule.reminder.api;

import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.reminder.ReminderService;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service = service; }

    @PostMapping("/api/v1/events/{id}/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<List<ReminderResponse>> create(@PathVariable UUID id, @RequestBody ReminderWriteRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.create(id, request, actor), correlation());
    }

    @GetMapping("/api/v1/reminders/me")
    ApiResponse<List<ReminderResponse>> mine(@RequestParam(required = false) String status,
                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.listMine(actor, status), correlation());
    }

    @DeleteMapping("/api/v1/reminders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) { service.cancel(id, actor); }

    private String correlation() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
