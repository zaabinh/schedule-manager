package vn.edu.school.schedule.academic.api;

import java.time.LocalDate;
import java.util.UUID;

public record AcademicYearResponse(UUID id, String name, LocalDate startDate,
                                   boolean isActive, long version, int weekCount) { }
