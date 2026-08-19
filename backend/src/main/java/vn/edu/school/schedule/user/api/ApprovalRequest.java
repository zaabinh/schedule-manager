package vn.edu.school.schedule.user.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
import java.util.UUID;

public record ApprovalRequest(
        @NotNull UUID departmentId,
        @NotEmpty Set<UUID> businessRoleIds,
        UUID homeroomClassId,
        @PositiveOrZero long version) { }
