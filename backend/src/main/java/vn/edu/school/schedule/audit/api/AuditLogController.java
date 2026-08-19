package vn.edu.school.schedule.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.audit.AuditLogService;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController @RequestMapping("/api/v1/audit-logs")
public class AuditLogController {
    private final AuditLogService service;
    public AuditLogController(AuditLogService service) { this.service = service; }
    @GetMapping ApiResponse<List<AuditLogResponse>> list(@RequestParam(required=false) UUID actorId,
            @RequestParam(required=false) String entityType, @RequestParam(required=false) String action,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue="100") int size) {
        return ApiResponse.success(service.list(actorId,entityType,action,from,to,size),cid());
    }
    @GetMapping("/{id}") ApiResponse<AuditLogResponse> get(@PathVariable UUID id) { return ApiResponse.success(service.get(id),cid()); }
    private String cid(){String value=MDC.get(CorrelationIdFilter.MDC_KEY);return value==null?"unavailable":value;}
}
