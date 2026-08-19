package vn.edu.school.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class CleanDatabaseMigrationIT {
    @Test
    void migratesCleanPostgresDatabase() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:18-alpine")) {
            postgres.start();
            var result = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load()
                    .migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(4);
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.prepareStatement(
                         "select count(*) from information_schema.tables where table_schema='public'");
                 var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(20);
            }
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.prepareStatement("""
                         select count(*) from information_schema.columns
                         where table_schema='public' and column_name='version'
                           and table_name in ('departments','business_roles','school_classes')
                         """);
                 var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(3);
            }
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.prepareStatement("""
                         select count(*) from information_schema.columns
                         where table_schema='public' and table_name='academic_years' and column_name='version'
                         """);
                 var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.prepareStatement("""
                         select count(*) from information_schema.tables
                         where table_schema='public' and table_name='api_idempotency_keys'
                         """);
                 var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
