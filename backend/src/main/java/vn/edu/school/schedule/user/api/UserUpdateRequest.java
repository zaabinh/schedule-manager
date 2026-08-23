package vn.edu.school.schedule.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UserUpdateRequest(
        @NotBlank @Size(max = 150) String displayName,
        @NotNull UUID departmentId,
        @NotEmpty Set<UUID> businessRoleIds,
        UUID homeroomClassId,
        @PositiveOrZero long version) { }
