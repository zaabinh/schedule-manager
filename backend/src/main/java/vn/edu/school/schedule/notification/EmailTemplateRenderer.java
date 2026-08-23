package vn.edu.school.schedule.notification;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateRenderer {
    private static final String SCHOOL_NAME = "TRƯỜNG THPT SỐ 2 PHAN BỘI CHÂU GIA LAI";
    private final String webUrl;

    public EmailTemplateRenderer(@Value("${app.email.web-url:http://localhost:3000}") String webUrl) {
        this.webUrl = normalizeWebUrl(webUrl);
    }

    public EmailMessage notification(String type, String detail) {
        String safeDetail = cleanText(detail);
        Model model = switch (type) {
            case "WEEKLY_PLAN_PUBLISHED" -> new Model(
                    "Kế hoạch tuần đã được công bố", "KẾ HOẠCH TUẦN", "Kế hoạch tuần mới đã sẵn sàng",
                    "Nhà trường đã công bố kế hoạch tuần mới. Bạn có thể mở hệ thống để xem lịch học, sự kiện và phân công liên quan.",
                    "Thông tin", safeDetail, "Xem kế hoạch tuần", "/weekly-plan");
            case "WEEKLY_PLAN_UPDATED" -> new Model(
                    "Kế hoạch tuần đã cập nhật", "CẬP NHẬT KẾ HOẠCH", "Kế hoạch tuần vừa có thay đổi",
                    "Nội dung kế hoạch đã được cập nhật. Vui lòng kiểm tra lại các hoạt động và phân công của bạn.",
                    "Nội dung cập nhật", safeDetail, "Xem nội dung mới", "/weekly-plan");
            case "TASK_ASSIGNED" -> new Model(
                    "Bạn có nhiệm vụ mới", "NHIỆM VỤ", "Bạn vừa được giao một nhiệm vụ",
                    "Một nhiệm vụ mới đã được phân công cho bạn. Hãy kiểm tra nội dung và thời hạn hoàn thành trên hệ thống.",
                    "Nhiệm vụ", safeDetail, "Xem nhiệm vụ", "/assignments");
            case "TASK_UPDATED" -> new Model(
                    "Nhiệm vụ đã cập nhật", "CẬP NHẬT NHIỆM VỤ", "Nhiệm vụ của bạn vừa được cập nhật",
                    "Thông tin của một nhiệm vụ đã thay đổi. Vui lòng mở hệ thống để xem nội dung mới nhất.",
                    "Nhiệm vụ", safeDetail, "Kiểm tra nhiệm vụ", "/assignments");
            case "SATURDAY_PLAN_REMINDER" -> new Model(
                    "Kế hoạch tuần tới chưa công bố", "NHẮC QUẢN TRỊ", "Kế hoạch tuần tới đang chờ công bố",
                    "Hệ thống chưa ghi nhận kế hoạch đã công bố cho tuần tiếp theo. Vui lòng kiểm tra và hoàn tất trước khi tuần mới bắt đầu.",
                    "Trạng thái", safeDetail, "Mở quản lý kế hoạch", "/admin/weekly-plans");
            case "CONVERSATION_OPENED" -> new Model(
                    "Có trao đổi mới", "TRAO ĐỔI", "Một cuộc trao đổi mới đã được mở",
                    "Bạn vừa nhận được một trao đổi mới trên hệ thống nội bộ của nhà trường.",
                    "Thông tin", safeDetail, "Xem trao đổi", "/conversations");
            case "CONVERSATION_MESSAGE" -> new Model(
                    "Có trao đổi mới", "TIN NHẮN MỚI", "Cuộc trao đổi có nội dung mới",
                    "Một cuộc trao đổi liên quan đến bạn vừa có phản hồi mới.",
                    "Thông tin", safeDetail, "Đọc trao đổi", "/conversations");
            case "CONVERSATION_CLOSED" -> new Model(
                    "Trao đổi đã đóng", "TRAO ĐỔI", "Cuộc trao đổi đã được đóng",
                    "Một cuộc trao đổi liên quan đến bạn đã được quản trị viên đóng trên hệ thống.",
                    "Trạng thái", safeDetail, "Xem lịch sử trao đổi", "/conversations");
            default -> new Model(
                    "Thông báo hệ thống", "THÔNG BÁO", "Bạn có thông báo mới",
                    "Hệ thống quản lý kế hoạch vừa gửi một thông báo đến tài khoản của bạn.",
                    "Thông tin", safeDetail, "Mở hệ thống", "/notifications");
        };
        return render(model);
    }

    public EmailMessage eventReminder(String eventTitle) {
        String title = cleanText(eventTitle);
        return render(new Model(
                "Nhắc lịch: " + title, "NHẮC LỊCH CÁ NHÂN", "Sự kiện của bạn sắp diễn ra",
                "Đây là nhắc lịch bạn đã thiết lập. Hãy kiểm tra thời gian, địa điểm và chuẩn bị cho sự kiện.",
                "Sự kiện", title, "Xem kế hoạch tuần", "/weekly-plan"));
    }

    private EmailMessage render(Model model) {
        String actionUrl = webUrl + model.path();
        String text = "%s\n\n%s\n%s: %s\n\n%s\n\n%s"
                .formatted(SCHOOL_NAME, model.description(), model.detailLabel(), model.detail(),
                        model.actionLabel() + ": " + actionUrl,
                        "Đây là email vận hành tự động, vui lòng không trả lời email này.");
        String html = """
                <!doctype html>
                <html lang="vi">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title></head>
                <body style="margin:0;padding:0;background:#f6f7f9;color:#172033;font-family:Arial,'Helvetica Neue',sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f6f7f9;">
                    <tr><td align="center" style="padding:32px 12px;">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:620px;background:#ffffff;border:1px solid #dfe7e4;border-radius:18px;overflow:hidden;box-shadow:0 8px 28px rgba(18,61,53,.08);">
                        <tr><td style="background:#123d35;padding:24px 28px;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"><tr>
                            <td width="54" valign="middle"><img src="%s" width="46" height="46" alt="PBC Gia Lai" style="display:block;width:46px;height:46px;border:0;border-radius:12px;background:#ffffff;object-fit:contain;"></td>
                            <td valign="middle" style="padding-left:12px;color:#ffffff;"><div style="font-size:10px;letter-spacing:1.2px;color:#bfe0d7;font-weight:700;">SỞ GIÁO DỤC VÀ ĐÀO TẠO TỈNH GIA LAI</div><div style="margin-top:4px;font-size:14px;line-height:20px;font-weight:800;">%s</div></td>
                          </tr></table>
                        </td></tr>
                        <tr><td style="padding:32px 28px 16px;">
                          <div style="font-size:11px;letter-spacing:1.4px;color:#185b4b;font-weight:800;">%s</div>
                          <h1 style="margin:10px 0 12px;font-size:26px;line-height:34px;color:#172033;">%s</h1>
                          <p style="margin:0;font-size:15px;line-height:24px;color:#586477;">%s</p>
                        </td></tr>
                        <tr><td style="padding:8px 28px 24px;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#eaf4f1;border-left:4px solid #185b4b;border-radius:10px;">
                            <tr><td style="padding:16px 18px;"><div style="font-size:11px;text-transform:uppercase;letter-spacing:1px;color:#547068;font-weight:700;">%s</div><div style="margin-top:6px;font-size:16px;line-height:24px;color:#10483b;font-weight:700;">%s</div></td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 28px 32px;"><a href="%s" style="display:inline-block;background:#185b4b;color:#ffffff;text-decoration:none;border-radius:9px;padding:13px 20px;font-size:14px;font-weight:700;">%s&nbsp;&nbsp;→</a></td></tr>
                        <tr><td style="border-top:1px solid #e7ecea;background:#fbfcfc;padding:20px 28px;color:#7a8594;font-size:12px;line-height:19px;">Email vận hành tự động từ hệ thống quản lý kế hoạch của nhà trường.<br>Vui lòng không trả lời email này.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(escapeHtml(model.subject()), escapeHtml(model.description()), escapeHtml(webUrl + "/school-logo.png"), SCHOOL_NAME,
                escapeHtml(model.eyebrow()), escapeHtml(model.heading()), escapeHtml(model.description()),
                escapeHtml(model.detailLabel()), escapeHtml(model.detail()), escapeHtml(actionUrl),
                escapeHtml(model.actionLabel()));
        return new EmailMessage(model.subject(), text, html);
    }

    private static String normalizeWebUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(normalized); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("app.email.web-url is invalid", failure); }
        boolean originOnly = uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getUserInfo() == null
                && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || !originOnly) {
            throw new IllegalArgumentException("app.email.web-url must be an absolute HTTP(S) origin");
        }
        return normalized;
    }

    private static String cleanText(String value) {
        if (value == null || value.isBlank()) return "Xem chi tiết trên hệ thống";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private record Model(String subject, String eyebrow, String heading, String description,
                         String detailLabel, String detail, String actionLabel, String path) { }
}
