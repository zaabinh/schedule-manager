package vn.edu.school.schedule.task.attachment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TaskAttachmentProperties {
    private final int maxFiles;
    private final long maxFileSizeBytes;
    private final long maxTotalSizeBytes;

    public TaskAttachmentProperties(
            @Value("${app.task-attachments.max-files}") int maxFiles,
            @Value("${app.task-attachments.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.task-attachments.max-total-size-bytes}") long maxTotalSizeBytes) {
        if (maxFiles < 1 || maxFileSizeBytes < 1 || maxTotalSizeBytes < maxFileSizeBytes) {
            throw new IllegalArgumentException("Cấu hình giới hạn Task attachment không hợp lệ.");
        }
        this.maxFiles = maxFiles;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxTotalSizeBytes = maxTotalSizeBytes;
    }

    public int maxFiles() { return maxFiles; }
    public long maxFileSizeBytes() { return maxFileSizeBytes; }
    public long maxTotalSizeBytes() { return maxTotalSizeBytes; }
}
