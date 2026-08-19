package vn.edu.school.schedule.weeklyplan.api;

import java.util.List;

public record PlanValidationResponse(boolean valid, List<PlanIssue> errors, List<PlanIssue> warnings) {
    public PlanValidationResponse(List<PlanIssue> errors, List<PlanIssue> warnings) {
        this(errors.isEmpty(), errors, warnings);
    }
}
