package vn.edu.school.schedule.academic.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SchoolWeekResponse(UUID id, UUID academicYearId, short sequenceNumber,
                                 short displayNumber, String weekType, LocalDate startDate,
                                 LocalDate endDate, long version, List<String> warnings) { }
