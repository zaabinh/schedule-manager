package vn.edu.school.schedule.task.api;

import jakarta.validation.constraints.Min;

public record CompleteTaskRequest(@Min(0) long version) { }
