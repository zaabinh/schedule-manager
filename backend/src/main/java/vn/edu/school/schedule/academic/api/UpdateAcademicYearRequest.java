package vn.edu.school.schedule.academic.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateAcademicYearRequest(
        @NotBlank @Size(max = 20) String name,
        @NotNull LocalDate startDate,
        @NotNull Boolean isActive,
        @Min(0) long version) { }
