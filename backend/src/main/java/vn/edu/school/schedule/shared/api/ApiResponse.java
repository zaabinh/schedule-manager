package vn.edu.school.schedule.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, ApiError error, ApiMeta meta) {
    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, null, new ApiMeta(correlationId));
    }

    public static <T> ApiResponse<java.util.List<T>> page(java.util.List<T> data, int page, int size,
                                                          long totalElements, String correlationId) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new ApiResponse<>(true, data, null, null,
                new ApiMeta(correlationId, page, size, totalElements, totalPages));
    }

    public static ApiResponse<Void> failure(ApiError error, String correlationId) {
        return new ApiResponse<>(false, null, null, error, new ApiMeta(correlationId));
    }
}
