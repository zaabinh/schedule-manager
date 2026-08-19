package vn.edu.school.schedule.weeklyplan.api;

import java.util.UUID;

public record DutyClassesWrite(UUID morningClassId, UUID afternoonClassId) { }
