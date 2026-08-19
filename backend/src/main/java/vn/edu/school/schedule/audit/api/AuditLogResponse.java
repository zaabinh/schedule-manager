package vn.edu.school.schedule.audit.api;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, Instant createdAt, String actor, String entityType, UUID entityId,
                               String action, String oldValue, String newValue, UUID correlationId) { }
