package vn.edu.school.schedule.weeklyplan.api;

import java.time.LocalDate;
import java.util.List;

public record PlanDayResponse(LocalDate date, String dayLabel, List<DaySessionResponse> sessions) { }
