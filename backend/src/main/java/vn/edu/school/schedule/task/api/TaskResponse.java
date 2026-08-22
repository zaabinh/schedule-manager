package vn.edu.school.schedule.task.api;

import java.time.Instant;
import java.util.UUID;
import vn.edu.school.schedule.auth.api.ResourceRef;

public record TaskResponse(UUID id, UUID weeklyPlanId, ResourceRef assignee, String title,
                           String description, Instant dueAt, String status, String displayStatus,
                           Instant completedAt, long version, long attachmentCount) { }
