package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DaySessionWrite(@NotNull LocalDate date,
                              @NotBlank @Pattern(regexp = "MORNING|AFTERNOON") String session,
                              @Size(max = 20000) String baseContent) { }
