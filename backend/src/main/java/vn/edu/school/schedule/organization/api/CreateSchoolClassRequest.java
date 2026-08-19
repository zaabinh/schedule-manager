package vn.edu.school.schedule.organization.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateSchoolClassRequest(
        @NotNull UUID academicYearId,
        @NotBlank @Size(max = 50) String name,
        @Min(10) @Max(12) short grade,
        UUID homeroomTeacherId) { }
