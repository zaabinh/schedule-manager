package vn.edu.school.schedule.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(String correlationId, Integer page, Integer size, Long totalElements, Integer totalPages) {
    public ApiMeta(String correlationId) {
        this(correlationId, null, null, null, null);
    }
}
