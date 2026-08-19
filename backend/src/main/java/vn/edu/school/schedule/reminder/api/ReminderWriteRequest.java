package vn.edu.school.schedule.reminder.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReminderWriteRequest(String preset, Instant remindAt, List<UUID> recipientUserIds) { }
