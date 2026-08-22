package vn.edu.school.schedule.task.attachment;

import java.time.Instant;
import java.util.UUID;

public record TaskAttachmentResponse(UUID id, UUID taskId, String originalName, String contentType,
                                     long fileSize, String checksum, Instant createdAt) { }
