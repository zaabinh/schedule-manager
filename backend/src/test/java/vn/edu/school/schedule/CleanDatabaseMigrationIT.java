package vn.edu.school.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
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
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                UUID first = UUID.randomUUID();
                UUID second = UUID.randomUUID();
                UUID third = UUID.randomUUID();
                insertActiveAdmin(connection, first, "admin-one@example.test");
                insertActiveAdmin(connection, second, "admin-two@example.test");

                assertThatThrownBy(() -> insertActiveAdmin(connection, third, "admin-three@example.test"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("At most two active Admin accounts are allowed");

                try (var slots = connection.prepareStatement("""
                        SELECT admin_slot FROM users
                        WHERE system_role='ADMIN' AND status='ACTIVE'
                        ORDER BY admin_slot
                        """)) {
                    var rows = slots.executeQuery();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                    assertThat(rows.next()).isFalse();
                }

                try (var deactivate = connection.prepareStatement("UPDATE users SET status='INACTIVE' WHERE id=?")) {
                    deactivate.setObject(1, first);
                    assertThat(deactivate.executeUpdate()).isEqualTo(1);
                }
                insertActiveAdmin(connection, third, "admin-three@example.test");
            }
        }
    }

    private static void insertActiveAdmin(java.sql.Connection connection, UUID id, String email) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO users(id,email,normalized_email,password_hash,display_name,system_role,status,approved_at)
                VALUES (?,?,?,?,?,'ADMIN','ACTIVE',now())
                """)) {
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setString(3, email);
            statement.setString(4, "test-password-hash");
            statement.setString(5, email);
            statement.executeUpdate();
        }
    }
}
