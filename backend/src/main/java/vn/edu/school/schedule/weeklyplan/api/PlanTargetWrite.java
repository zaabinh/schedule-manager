package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record PlanTargetWrite(
        @NotBlank @Pattern(regexp = "ALL|ROLE|DEPARTMENT|USER") String targetType,
        UUID targetId) { }
