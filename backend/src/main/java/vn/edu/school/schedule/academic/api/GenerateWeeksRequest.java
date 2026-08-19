package vn.edu.school.schedule.academic.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateWeeksRequest(@NotNull @Min(39) @Max(39) Integer count) { }
