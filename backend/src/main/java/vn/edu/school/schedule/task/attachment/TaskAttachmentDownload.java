package vn.edu.school.schedule.task.attachment;

import org.springframework.core.io.Resource;

public record TaskAttachmentDownload(Resource resource, String originalName, String contentType, long fileSize) { }
