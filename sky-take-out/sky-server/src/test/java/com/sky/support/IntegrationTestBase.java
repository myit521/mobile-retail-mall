package com.sky.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.lifecycle.Startables;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Stream;

public abstract class IntegrationTestBase {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8")
            .withDatabaseName("sky_test")
            .withUsername("sky")
            .withPassword("sky");

    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    protected static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3")
            .withUser("sky", "sky");

    static {
        Startables.deepStart(Stream.of(MYSQL, REDIS, RABBITMQ)).join();
        grantFlywayPerformanceSchemaAccess();
    }

    private static void grantFlywayPerformanceSchemaAccess() {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("GRANT SELECT ON performance_schema.user_variables_by_thread TO 'sky'@'%'");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to grant Flyway test access to Performance Schema", exception);
        }
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.datasource.druid.driver-class-name", MYSQL::getDriverClassName);
        registry.add("sky.datasource.host", MYSQL::getHost);
        registry.add("sky.datasource.port", () -> MYSQL.getMappedPort(3306));
        registry.add("sky.datasource.database", MYSQL::getDatabaseName);
        registry.add("sky.datasource.username", MYSQL::getUsername);
        registry.add("sky.datasource.password", MYSQL::getPassword);
        registry.add("sky.datasource.driver-class-name", MYSQL::getDriverClassName);

        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.redis.database", () -> 0);
        registry.add("spring.redis.password", () -> "");
        registry.add("sky.redis.host", REDIS::getHost);
        registry.add("sky.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("sky.redis.database", () -> 0);
        registry.add("sky.redis.password", () -> "");

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("sky.rabbitmq.host", RABBITMQ::getHost);
        registry.add("sky.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("sky.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("sky.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}
