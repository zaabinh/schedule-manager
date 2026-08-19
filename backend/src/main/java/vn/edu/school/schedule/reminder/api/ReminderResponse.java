package vn.edu.school.schedule.reminder.api;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(UUID id, UUID eventId, String eventTitle, UUID ownerUserId,
                               String source, Instant remindAt, String status, int attemptCount,
                               Instant sentAt) { }
