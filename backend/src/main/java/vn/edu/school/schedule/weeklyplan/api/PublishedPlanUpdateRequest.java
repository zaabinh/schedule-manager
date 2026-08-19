package vn.edu.school.schedule.weeklyplan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PublishedPlanUpdateRequest(
        @Min(0) long version,
        @NotNull @Size(min = 5, max = 5) List<@Valid PlanSectionWrite> sections,
        @NotNull @Valid DutyClassesWrite dutyClasses,
        @NotNull List<@Valid DaySessionWrite> daySessions,
        boolean notifyWebsite,
        boolean notifyEmail) {
    public WeeklyPlanUpdateRequest plan() {
        return new WeeklyPlanUpdateRequest(version, sections, dutyClasses, daySessions);
    }
}
