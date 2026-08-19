package vn.edu.school.schedule.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.school.schedule.shared.api.ApiError;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.api.ApiFieldError;
import vn.edu.school.schedule.shared.api.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> api(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(
                new ApiError(exception.code(), exception.getMessage(), exception.details()), correlationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
        List<ApiFieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(
                new ApiError("VALIDATION_FAILED", "Dữ liệu không hợp lệ.", details), correlationId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        request.getServletContext().log("Unhandled request failure, correlationId=" + correlationId(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(
                new ApiError("INTERNAL_ERROR", "Hệ thống không thể xử lý yêu cầu.", List.of()), correlationId()));
    }

    private String correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        return value == null ? "unavailable" : value;
    }
}
