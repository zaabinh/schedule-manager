package vn.edu.school.schedule.notification.api;
import java.time.Instant;
import java.util.UUID;
public record NotificationResponse(UUID id,String type,String title,String description,UUID entityId,Instant createdAt,Instant readAt) { }
