package vn.edu.school.schedule.auth;

import java.util.Locale;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.school.schedule.shared.api.ApiException;
import vn.edu.school.schedule.shared.api.ApiFieldError;

@Component
public class PasswordPolicy {
    private static final Set<String> BLOCKED = Set.of(
            "passwordpassword", "123456789012345", "qwertyuiopasdfg", "matkhau123456789");

    public void validate(String password) {
        boolean hasLowercase = password != null && password.codePoints().anyMatch(Character::isLowerCase);
        boolean hasUppercase = password != null && password.codePoints().anyMatch(Character::isUpperCase);
        boolean hasSpecial = password != null && password.codePoints()
                .anyMatch(value -> !Character.isLetterOrDigit(value) && !Character.isWhitespace(value));
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !hasLowercase || !hasUppercase || !hasSpecial
                || BLOCKED.contains(password.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PASSWORD_POLICY_FAILED",
                    "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ thường, chữ hoa và ký tự đặc biệt.",
                    java.util.List.of(new ApiFieldError("password", "Mật khẩu không đáp ứng chính sách.")));
        }
    }
}
