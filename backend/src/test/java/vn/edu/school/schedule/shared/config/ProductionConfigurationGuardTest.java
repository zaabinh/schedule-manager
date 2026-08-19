package vn.edu.school.schedule.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionConfigurationGuardTest {
    @Test void acceptsSafeProductionConfiguration() {
        var guard=new ProductionConfigurationGuard(true,"__Host-session","a-random-production-pepper-at-least-32-chars","https://schedule.example.edu.vn",false,"smtp","a-random-database-password-at-least-16");
        assertThatCode(guard::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
    @Test void rejectsLocalDefaultsInProduction() {
        var guard=new ProductionConfigurationGuard(false,"session","local-development-pepper-change-me","http://localhost:3000",true,"log","schedule_local_password");
        assertThatThrownBy(guard::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class).hasMessageContaining("Production configuration rejected");
    }

    @Test void rejectsCookieWithoutHostPrefixInProduction() {
        var guard=new ProductionConfigurationGuard(true,"session","a-random-production-pepper-at-least-32-chars","https://schedule.example.edu.vn",false,"smtp","a-random-database-password-at-least-16");
        assertThatThrownBy(guard::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("__Host-");
    }
}
