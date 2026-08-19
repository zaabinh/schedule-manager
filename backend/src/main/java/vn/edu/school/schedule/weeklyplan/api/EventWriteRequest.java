package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record EventWriteRequest(
        @NotBlank @Size(max = 20000) String content,
        LocalDate startDate,
        LocalDate endDate,
        @Pattern(regexp = "MORNING|AFTERNOON") String session,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 255) String location,
        @Size(max = 20000) String note,
        Long version,
        Boolean notifyWebsite,
        Boolean notifyEmail) { }
