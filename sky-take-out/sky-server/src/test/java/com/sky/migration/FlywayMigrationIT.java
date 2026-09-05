package com.sky.migration;

import com.sky.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends IntegrationTestBase {

    private final List<String> databasesToDrop = new ArrayList<>();

    @AfterEach
    void dropTestDatabases() throws Exception {
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement()) {
            for (String database : databasesToDrop) {
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
        }
    }

    @Test
    void migratesEmptyMySqlDatabaseToVersionOneAndRerunIsANoOp() throws Exception {
        String database = createDatabase();

        Flyway flyway = configuredFlyway(database);
        MigrateResult firstMigration = flyway.migrate();

        assertThat(firstMigration.success).isTrue();
        assertThat(firstMigration.migrationsExecuted).isEqualTo(1);
        assertThat(tableNames(database)).contains(
                "category", "employee", "id_segment", "memo", "order_detail", "orders",
                "payment_callback_log", "phone_model", "product", "product_location",
                "product_phone_model", "product_spec", "shopping_cart", "stock_alert",
                "stock_check_plan", "stock_check_record", "stock_log", "user");
        assertThat(flyway.info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("1"));

        MigrateResult rerun = flyway.migrate();

        assertThat(rerun.success).isTrue();
        assertThat(rerun.migrationsExecuted).isZero();
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .containsExactly(MigrationVersion.fromVersion("1"));
        assertThat(migrationSuccess(database)).containsExactly(true);
    }

    @Test
    void baselinesPreloadedLegacySchemaAtVersionOneWithoutReplayingBaselineDdl() throws Exception {
        String database = createDatabase();
        try (Connection connection = connectionTo(database)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/sky.sql"));
        }

        Flyway flyway = configuredFlyway(database);
        MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("1"));
        assertThat(tableNames(database)).contains("category", "employee", "orders", "product", "user");
        assertThat(migrationSuccess(database)).containsExactly(true);
    }

    private Flyway configuredFlyway(String database) {
        return Flyway.configure()
                .dataSource(jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .validateOnMigrate(true)
                .load();
    }

    private String createDatabase() throws Exception {
        String database = "flyway_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "`");
            statement.execute("GRANT ALL PRIVILEGES ON `" + database + "`.* TO '" + MYSQL.getUsername() + "'@'%'");
        }
        databasesToDrop.add(database);
        return database;
    }

    private List<String> tableNames(String database) throws Exception {
        List<String> tableNames = new ArrayList<>();
        try (Connection connection = connectionTo(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = '" + database + "' ORDER BY table_name")) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString(1));
            }
        }
        return tableNames;
    }

    private List<Boolean> migrationSuccess(String database) throws Exception {
        List<Boolean> success = new ArrayList<>();
        try (Connection connection = connectionTo(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT success FROM `flyway_schema_history` ORDER BY installed_rank")) {
            while (resultSet.next()) {
                success.add(resultSet.getBoolean(1));
            }
        }
        return success;
    }

    private Connection connectionTo(String database) throws Exception {
        return DriverManager.getConnection(jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private Connection adminConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl(""), "root", MYSQL.getPassword());
    }

    private String jdbcUrl(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database;
    }
}
