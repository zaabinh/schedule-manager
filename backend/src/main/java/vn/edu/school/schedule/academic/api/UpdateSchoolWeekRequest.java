package vn.edu.school.schedule.academic.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record UpdateSchoolWeekRequest(
        @Min(1) short displayNumber,
        @NotBlank @Pattern(regexp = "ORIENTATION|STUDY") String weekType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(0) long version) { }
