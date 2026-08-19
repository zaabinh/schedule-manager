package vn.edu.school.schedule.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void successBuildsDocumentedEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ok", "correlation-id");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.error()).isNull();
        assertThat(response.meta().correlationId()).isEqualTo("correlation-id");
    }
}
