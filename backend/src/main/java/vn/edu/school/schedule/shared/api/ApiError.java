package vn.edu.school.schedule.shared.api;

import java.util.List;

public record ApiError(String code, String message, List<ApiFieldError> details) {
    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
