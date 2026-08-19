package vn.edu.school.schedule.user.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record StatusRequest(
        @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @PositiveOrZero long version) { }
