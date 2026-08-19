package vn.edu.school.schedule.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import vn.edu.school.schedule.shared.web.CorrelationIdFilter;

final class SecurityJson {
    private SecurityJson() { }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) correlationId = "unavailable";
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"" + code
                + "\",\"message\":\"" + message + "\",\"details\":[]},\"meta\":{\"correlationId\":\""
                + correlationId + "\"}}");
    }
}
