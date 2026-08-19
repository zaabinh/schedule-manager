package vn.edu.school.schedule.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description) { }
