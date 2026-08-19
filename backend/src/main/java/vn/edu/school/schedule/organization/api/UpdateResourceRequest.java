package vn.edu.school.schedule.organization.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateResourceRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotNull Boolean isActive,
        @Min(0) long version) { }
