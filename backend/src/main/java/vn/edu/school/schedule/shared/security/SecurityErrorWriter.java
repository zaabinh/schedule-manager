package vn.edu.school.schedule.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class SecurityErrorWriter {
    private SecurityErrorWriter() { }
    public static void unauthorized(HttpServletResponse response) throws IOException {
        SecurityJson.write(response, 401, "UNAUTHENTICATED", "Yêu cầu đăng nhập.");
    }
    public static void forbidden(HttpServletResponse response) throws IOException {
        SecurityJson.write(response, 403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
    }
}
