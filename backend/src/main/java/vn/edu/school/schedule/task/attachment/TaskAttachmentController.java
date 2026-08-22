package vn.edu.school.schedule.task.attachment;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.school.schedule.shared.api.ApiResponse;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1")
public class TaskAttachmentController {
    private final TaskAttachmentService attachments;

    public TaskAttachmentController(TaskAttachmentService attachments) { this.attachments = attachments; }

    @PostMapping(path = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<TaskAttachmentResponse> upload(@PathVariable UUID taskId, @RequestPart("file") MultipartFile file,
                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(attachments.upload(taskId, file, actor), correlation());
    }

    @GetMapping("/tasks/{taskId}/attachments")
    ApiResponse<List<TaskAttachmentResponse>> list(@PathVariable UUID taskId,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ApiResponse.success(attachments.list(taskId, actor), correlation());
    }

    @GetMapping("/task-attachments/{id}/download")
    ResponseEntity<Resource> download(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        TaskAttachmentDownload download = attachments.download(id, actor);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    @DeleteMapping("/task-attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        attachments.delete(id, actor);
    }

    private String correlation() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
