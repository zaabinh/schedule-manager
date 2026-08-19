package vn.edu.school.schedule.weeklyplan.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EventResponse(UUID id, String content, LocalDate startDate, LocalDate endDate,
                            String session, LocalTime startTime, LocalTime endTime,
                            String location, String note, long version) { }
