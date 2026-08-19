package vn.edu.school.schedule.task.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TaskWriteRequest(@NotNull UUID weeklyPlanId, @NotNull UUID assigneeUserId,
                               @NotBlank @Size(max=255) String title,
                               @Size(max=5000) String description,
                               @NotNull Instant dueAt, Long version) { }
