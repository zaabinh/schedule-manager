package vn.edu.school.schedule.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ExternalDatabaseUrlTest {
    @Test void convertsRenderConnectionStringWithoutLoggingCredentials() {
        var connection = ExternalDatabaseUrl.parse(
                "postgresql://render_user:p%40ss%3Aword@dpg.internal:5432/schedule_manager?sslmode=require");

        assertThat(connection.jdbcUrl())
                .isEqualTo("jdbc:postgresql://dpg.internal:5432/schedule_manager?sslmode=require");
        assertThat(connection.username()).isEqualTo("render_user");
        assertThat(connection.password()).isEqualTo("p@ss:word");
    }

    @Test void appliesExternalUrlOnlyWhenExplicitJdbcUrlIsAbsent() {
        var properties = new Properties();
        ExternalDatabaseUrl.apply(Map.of("DATABASE_URL", "postgres://user:secret@db.internal/app"), properties);
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.internal:5432/app");

        var explicitProperties = new Properties();
        var environment = new HashMap<String, String>();
        environment.put("DB_URL", "jdbc:postgresql://explicit/db");
        environment.put("DATABASE_URL", "postgres://ignored:ignored@db.internal/app");
        ExternalDatabaseUrl.apply(environment, explicitProperties);
        assertThat(explicitProperties).isEmpty();
    }

    @Test void rejectsIncompleteOrNonPostgresUrls() {
        assertThatThrownBy(() -> ExternalDatabaseUrl.parse("https://db.internal/app"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgres");
        assertThatThrownBy(() -> ExternalDatabaseUrl.parse("postgresql://db.internal/app"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials");
    }
}
