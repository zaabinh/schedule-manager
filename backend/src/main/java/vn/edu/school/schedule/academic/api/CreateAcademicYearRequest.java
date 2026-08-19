package vn.edu.school.schedule.academic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateAcademicYearRequest(
        @NotBlank @Size(max = 20) String name,
        @NotNull LocalDate startDate,
        Boolean isActive,
        Boolean generateWeeks) { }
