package vn.edu.school.schedule.weeklyplan.api;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edu.school.schedule.auth.api.ResourceRef;

public record WeeklyPlanResponse(UUID id, UUID weekId, short sequenceNumber, String displayLabel,
                                 LocalDate startDate, LocalDate endDate, String status, long version,
                                 Instant publishedAt, UUID publishedBy,
                                 ResourceRef morningDutyClass, ResourceRef afternoonDutyClass,
                                 List<PlanSectionResponse> sections, List<PlanDayResponse> days) { }
