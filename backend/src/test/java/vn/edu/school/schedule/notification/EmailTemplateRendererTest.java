package vn.edu.school.schedule.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer("https://schedule.example.edu.vn/");

    @Test
    void rendersEveryOperationalEmailTypeWithBrandAndMatchingAction() {
        List<String> types = List.of("WEEKLY_PLAN_PUBLISHED", "WEEKLY_PLAN_UPDATED", "TASK_ASSIGNED",
                "TASK_UPDATED", "SATURDAY_PLAN_REMINDER", "CONVERSATION_OPENED",
                "CONVERSATION_MESSAGE", "CONVERSATION_CLOSED");

        for (String type : types) {
            EmailMessage message = renderer.notification(type, "Nội dung kiểm thử");
            assertThat(message.subject()).isNotBlank();
            assertThat(message.textBody()).contains("TRƯỜNG THPT SỐ 2 PHAN BỘI CHÂU GIA LAI")
                    .contains("https://schedule.example.edu.vn/");
            assertThat(message.htmlBody()).contains("<!doctype html>")
                    .contains("background:#123d35")
                    .contains("https://schedule.example.edu.vn/school-logo.png")
                    .contains("Nội dung kiểm thử")
                    .contains("https://schedule.example.edu.vn/");
        }
    }

    @Test
    void escapesDynamicContentAndKeepsPlainTextFallback() {
        EmailMessage message = renderer.eventReminder("Họp <script>alert('x')</script>\nkhẩn");

        assertThat(message.subject()).doesNotContain("\n").startsWith("Nhắc lịch:");
        assertThat(message.textBody()).contains("Họp <script>");
        assertThat(message.htmlBody()).contains("&lt;script&gt;")
                .doesNotContain("<script>alert");
    }

    @Test
    void rejectsNonHttpEmailActionOrigin() {
        assertThatThrownBy(() -> new EmailTemplateRenderer("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP(S)");
    }
}
