package vn.edu.school.schedule.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.edu.school.schedule.shared.api.ApiException;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsPasswordWithRequiredCharacterGroups() {
        policy.validate("Abcdefg@");
    }

    @Test
    void rejectsMissingCharacterGroupsShortCommonAndOver72BytePasswords() {
        assertRejected("Aa@123");
        assertRejected("matkhau@2026");
        assertRejected("MATKHAU@2026");
        assertRejected("MatKhau2026");
        assertRejected("MatKhau 2026");
        assertRejected("123456789012345");
        assertRejected("A@" + "á".repeat(36));
    }

    private void assertRejected(String password) {
        assertThatThrownBy(() -> policy.validate(password))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("PASSWORD_POLICY_FAILED");
    }
}
