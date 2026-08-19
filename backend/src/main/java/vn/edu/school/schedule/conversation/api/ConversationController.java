package vn.edu.school.schedule.conversation.api;

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
import vn.edu.school.schedule.conversation.ConversationService;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ConversationResponse> create(@RequestBody ConversationWriteRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.create(request, actor), cid());
    }
    @GetMapping ApiResponse<List<ConversationResponse>> list(@RequestParam(required = false) String status, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.list(actor, status), cid());
    }
    @GetMapping("/{id}") ApiResponse<ConversationResponse> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.get(id, actor), cid());
    }
    @GetMapping("/{id}/messages") ApiResponse<List<ConversationMessageResponse>> messages(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.get(id, actor).messages(), cid());
    }
    @PostMapping("/{id}/messages") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ConversationResponse> send(@PathVariable UUID id, @RequestBody MessageWriteRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.send(id, request, actor), cid());
    }
    @PatchMapping("/{id}/close") ApiResponse<ConversationResponse> close(@PathVariable UUID id, @RequestBody CloseConversationRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(service.close(id, request, actor), cid());
    }
    private String cid() { String value = MDC.get(CorrelationIdFilter.MDC_KEY); return value == null ? "unavailable" : value; }
}
