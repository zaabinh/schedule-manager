package vn.edu.school.schedule.shared.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

public final class ExternalDatabaseUrl {
    private ExternalDatabaseUrl() {}

    public static void applyFromEnvironment() {
        apply(System.getenv(), System.getProperties());
    }

    static void apply(Map<String, String> environment, Properties properties) {
        if (StringUtils.hasText(environment.get("DB_URL"))
                || StringUtils.hasText(properties.getProperty("spring.datasource.url"))) {
            return;
        }
        String externalUrl = environment.get("DATABASE_URL");
        if (!StringUtils.hasText(externalUrl)) return;

        DatabaseConnection connection = parse(externalUrl);
        properties.setProperty("spring.datasource.url", connection.jdbcUrl());
        properties.putIfAbsent("spring.datasource.username", connection.username());
        properties.putIfAbsent("spring.datasource.password", connection.password());
    }

    static DatabaseConnection parse(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATABASE_URL is not a valid PostgreSQL URL", exception);
        }

        if (!("postgres".equals(uri.getScheme()) || "postgresql".equals(uri.getScheme()))) {
            throw new IllegalStateException("DATABASE_URL must use postgres:// or postgresql://");
        }
        if (!StringUtils.hasText(uri.getHost()) || !StringUtils.hasText(uri.getRawUserInfo())) {
            throw new IllegalStateException("DATABASE_URL must include host and credentials");
        }

        String[] credentials = uri.getRawUserInfo().split(":", 2);
        if (credentials.length != 2 || !StringUtils.hasText(credentials[0]) || !StringUtils.hasText(credentials[1])) {
            throw new IllegalStateException("DATABASE_URL must include username and password");
        }
        String rawPath = uri.getRawPath();
        if (!StringUtils.hasText(rawPath) || "/".equals(rawPath)) {
            throw new IllegalStateException("DATABASE_URL must include a database name");
        }

        String host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        String database = UriUtils.decode(rawPath.substring(1), StandardCharsets.UTF_8);
        String query = StringUtils.hasText(uri.getRawQuery()) ? "?" + uri.getRawQuery() : "";
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database + query;

        return new DatabaseConnection(
                jdbcUrl,
                UriUtils.decode(credentials[0], StandardCharsets.UTF_8),
                UriUtils.decode(credentials[1], StandardCharsets.UTF_8));
    }

    record DatabaseConnection(String jdbcUrl, String username, String password) {}
}
