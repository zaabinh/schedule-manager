package vn.edu.school.schedule.shared.config;

import java.util.Arrays;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigurationGuard implements SmartInitializingSingleton {
    private final boolean secureCookie;
    private final String cookieName;
    private final String pepper;
    private final String origins;
    private final boolean bootstrapAdmin;
    private final String emailProvider;
    private final String databasePassword;

    public ProductionConfigurationGuard(@Value("${app.security.cookie-secure}") boolean secureCookie,
            @Value("${app.security.cookie-name}") String cookieName,
            @Value("${app.security.session-pepper}") String pepper,
            @Value("${app.security.allowed-origins}") String origins,
            @Value("${app.bootstrap-admin.enabled}") boolean bootstrapAdmin,
            @Value("${app.email.provider}") String emailProvider,
            @Value("${spring.datasource.password}") String databasePassword) {
        this.secureCookie = secureCookie;
        this.cookieName = cookieName;
        this.pepper = pepper;
        this.origins = origins;
        this.bootstrapAdmin = bootstrapAdmin;
        this.emailProvider = emailProvider;
        this.databasePassword = databasePassword;
    }

    @Override
    public void afterSingletonsInstantiated() {
        require(secureCookie, "SESSION_COOKIE_SECURE must be true in prod");
        require(cookieName.startsWith("__Host-") && cookieName.length() > "__Host-".length(),
                "SESSION_COOKIE_NAME must use the __Host- prefix in prod");
        require(pepper.length() >= 32 && !pepper.contains("local-development") && !pepper.contains("replace-with"),
                "SESSION_PEPPER is unsafe");
        require(Arrays.stream(origins.split(",")).map(String::trim).allMatch(origin -> origin.startsWith("https://")),
                "CORS origins must use HTTPS in prod");
        require(!bootstrapAdmin, "BOOTSTRAP_ADMIN_ENABLED must be false after provisioning");
        require("smtp".equals(emailProvider), "EMAIL_PROVIDER must be smtp in prod");
        require(!"schedule_local_password".equals(databasePassword) && databasePassword.length() >= 16,
                "DB_PASSWORD is unsafe");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Production configuration rejected: " + message);
        }
    }
}
