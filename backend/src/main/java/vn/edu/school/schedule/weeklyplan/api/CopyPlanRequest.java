package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CopyPlanRequest(@NotNull UUID sourceWeekId) { }
