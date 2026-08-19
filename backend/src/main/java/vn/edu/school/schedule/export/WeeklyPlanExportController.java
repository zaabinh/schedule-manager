package vn.edu.school.schedule.export;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.school.schedule.shared.security.AuthenticatedUser;

@RestController @RequestMapping("/api/v1/weekly-plans")
public class WeeklyPlanExportController {
    private static final MediaType XLSX=MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final WeeklyPlanExcelExporter exporter;
    public WeeklyPlanExportController(WeeklyPlanExcelExporter exporter){this.exporter=exporter;}
    @GetMapping("/{id}/export") ResponseEntity<byte[]> export(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser actor){var file=exporter.export(id,actor);return ResponseEntity.ok().contentType(XLSX).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.fileName(),StandardCharsets.UTF_8).build().toString()).body(file.content());}
}
