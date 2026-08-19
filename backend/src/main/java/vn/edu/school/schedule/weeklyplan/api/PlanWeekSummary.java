package vn.edu.school.schedule.weeklyplan.api;

import java.time.LocalDate;
import java.util.UUID;

public record PlanWeekSummary(UUID id, UUID academicYearId, short sequenceNumber, String label,
                              LocalDate startDate, LocalDate endDate, String planStatus) { }
