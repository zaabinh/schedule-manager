package vn.edu.school.schedule.weeklyplan.api;

import java.util.List;

public record DaySessionResponse(String session, String baseContent, List<EventResponse> events) { }
