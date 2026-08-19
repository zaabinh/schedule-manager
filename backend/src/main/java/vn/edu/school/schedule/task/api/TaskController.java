package vn.edu.school.schedule.task.api;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;
import vn.edu.school.schedule.task.TaskService;

@RestController @RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService tasks; public TaskController(TaskService tasks){this.tasks=tasks;}
    @GetMapping ApiResponse<List<TaskResponse>> list(){return ApiResponse.success(tasks.listAll(),correlation());}
    @GetMapping("/summary") ApiResponse<TaskSummary> summary(){return ApiResponse.success(tasks.summary(),correlation());}
    @GetMapping("/options") ApiResponse<TaskOptions> options(){return ApiResponse.success(tasks.options(),correlation());}
    @GetMapping("/me") ApiResponse<List<TaskResponse>> mine(@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(tasks.listMine(actor),correlation());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) ApiResponse<TaskResponse> create(@Valid @RequestBody TaskWriteRequest request,@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(tasks.create(request,actor),correlation());}
    @PatchMapping("/{id}") ApiResponse<TaskResponse> update(@PathVariable UUID id,@Valid @RequestBody TaskWriteRequest request,@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(tasks.update(id,request,actor),correlation());}
    @PatchMapping("/{id}/complete") ApiResponse<TaskResponse> complete(@PathVariable UUID id,@Valid @RequestBody CompleteTaskRequest request,@AuthenticationPrincipal AuthenticatedUser actor){return ApiResponse.success(tasks.complete(id,request.version(),actor),correlation());}
    private String correlation(){String value=MDC.get(CorrelationIdFilter.MDC_KEY);return value==null?"unavailable":value;}
}
