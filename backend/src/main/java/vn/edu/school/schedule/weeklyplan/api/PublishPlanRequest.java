package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.constraints.Min;

public record PublishPlanRequest(@Min(0) long version, boolean publishWithWarnings) { }
