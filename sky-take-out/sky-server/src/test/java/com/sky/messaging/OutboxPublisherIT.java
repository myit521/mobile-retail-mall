package com.sky.messaging;

import com.sky.constant.RabbitMQConstant;
import com.sky.entity.OutboxEvent;
import com.sky.messaging.outbox.OutboxEventMapper;
import com.sky.messaging.outbox.OutboxPublisher;
import com.sky.support.IntegrationTestBase;
import com.sky.observability.BusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublisherIT extends IntegrationTestBase {
    private static final String TEST_QUEUE = "outbox.publisher.it";
    private static final String TEST_VHOST = "outbox-publisher-it-" + UUID.randomUUID();
    private static final String TEST_DATABASE = "outbox_publisher_it_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate jdbc;
    private OutboxEventMapper events;
    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbit;
    private SimpleMeterRegistry meterRegistry;

    @BeforeAll
    static void isolateBrokerTopology() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(""), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + TEST_DATABASE + "`");
            statement.execute("GRANT ALL PRIVILEGES ON `" + TEST_DATABASE + "`.* TO '" + MYSQL.getUsername() + "'@'%'");
        }
        assertThat(RABBITMQ.execInContainer("rabbitmqctl", "add_vhost", TEST_VHOST).getExitCode()).isZero();
        assertThat(RABBITMQ.execInContainer("rabbitmqctl", "set_permissions", "-p", TEST_VHOST,
                RABBITMQ.getAdminUsername(), ".*", ".*", ".*").getExitCode()).isZero();
    }

    @AfterAll
    static void removeBrokerTopology() throws Exception {
        try {
            assertThat(RABBITMQ.execInContainer("rabbitmqctl", "delete_vhost", TEST_VHOST).getExitCode()).isZero();
        } finally {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(""), "root", MYSQL.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + TEST_DATABASE + "`");
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl(TEST_DATABASE), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM outbox_event WHERE business_key LIKE 'OUTBOX_IT:%'");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OutboxEventMapper.class);
        factory.setConfiguration(configuration);
        events = new SqlSessionTemplate(factory.getObject()).getMapper(OutboxEventMapper.class);
        connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());
        connectionFactory.setVirtualHost(TEST_VHOST);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connectionFactory);
        rabbit.setMandatory(true);
        meterRegistry = new SimpleMeterRegistry();
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(new DirectExchange(RabbitMQConstant.ORDER_EVENT_EXCHANGE, true, false));
        admin.deleteQueue(TEST_QUEUE);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            new RabbitAdmin(connectionFactory).deleteQueue(TEST_QUEUE);
            connectionFactory.destroy();
        }
    }

    @Test
    void usesDedicatedDatabaseSchema() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(TEST_DATABASE);
    }

    @Test
    void pendingEventSurvivesProcessGapAndIsDeliveredAfterPublisherStarts() throws Exception {
        String eventId = insertPending("RECOVERY");
        jdbc.update("UPDATE outbox_event SET status='SENDING', lease_owner='dead-process', lease_expires_at=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE event_id=?", eventId);
        assertThat(status(eventId)).isEqualTo("SENDING");
        declareBoundQueue();
        assertThat(publisher("restart-worker").publishNext()).isTrue();
        assertThat(status(eventId)).isEqualTo("SENT");
        assertThat(rabbit.receive(TEST_QUEUE, 5_000)).isNotNull();
    }

    @Test
    void mandatoryReturnLeavesEventQueryableWithPersistedBackoff() {
        String eventId = insertPending("RETURN");
        assertThat(publisher("return-worker").publishNext()).isTrue();
        assertThat(jdbc.queryForMap("SELECT status, attempt_count, next_attempt_at, last_error FROM outbox_event WHERE event_id=?", eventId))
                .containsEntry("status", "PENDING").containsEntry("attempt_count", 1)
                .containsEntry("last_error", "message-returned");
        assertThat(meterRegistry.get("sky.outbox.publish.failures").tag("reason", "returned")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void repeatedReturnsReachTerminalQueryableFailure() {
        String eventId = insertPending("TERMINAL");
        OutboxPublisher publisher = publisher("terminal-worker");
        assertThat(publisher.publishNext()).isTrue();
        jdbc.update("UPDATE outbox_event SET next_attempt_at=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE event_id=?", eventId);
        assertThat(publisher.publishNext()).isTrue();
        jdbc.update("UPDATE outbox_event SET next_attempt_at=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE event_id=?", eventId);
        assertThat(publisher.publishNext()).isTrue();

        assertThat(jdbc.queryForMap("SELECT status, attempt_count, next_attempt_at, last_error FROM outbox_event WHERE event_id=?", eventId))
                .containsEntry("status", "FAILED").containsEntry("attempt_count", 3)
                .containsEntry("last_error", "message-returned").containsEntry("next_attempt_at", null);
        assertThat(publisher.publishNext()).isFalse();
    }

    @Test
    void concurrentPublishersCannotClaimTheSameEventTwice() throws Exception {
        declareBoundQueue();
        String eventId = insertPending("CONCURRENT");
        OutboxPublisher first = publisher("worker-a");
        OutboxPublisher second = publisher("worker-b");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Boolean>> results = List.of(
                    pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return first.publishNext(); }),
                    pool.submit(() -> { start.await(5, TimeUnit.SECONDS); return second.publishNext(); }));
            start.countDown();
            assertThat(List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(status(eventId)).isEqualTo("SENT");
        assertThat(rabbit.receive(TEST_QUEUE, 5_000)).isNotNull();
        assertThat(rabbit.receive(TEST_QUEUE, 200)).isNull();
    }

    private OutboxPublisher publisher(String worker) {
        OutboxPublisher publisher = new OutboxPublisher(events, rabbit, worker, Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(5), 3, 512);
        ReflectionTestUtils.setField(publisher, "metrics", new BusinessMetrics(meterRegistry));
        return publisher;
    }

    private void declareBoundQueue() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        DirectExchange exchange = new DirectExchange(RabbitMQConstant.ORDER_EVENT_EXCHANGE, true, false);
        Queue queue = new Queue(TEST_QUEUE, true, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(RabbitMQConstant.ORDER_PAID_ROUTING_KEY));
        assertThat(RABBITMQ.execInContainer("rabbitmqctl", "list_queues", "-p", TEST_VHOST,
                "name", "durable", "exclusive", "auto_delete").getStdout())
                .containsPattern("outbox\\.publisher\\.it\\s+true\\s+false\\s+false");
    }

    private String insertPending(String suffix) {
        String id = UUID.randomUUID().toString();
        OutboxEvent event = OutboxEvent.builder().eventId(id).correlationId("correlation-" + suffix)
                .eventType("ORDER_PAID").payloadVersion(1).aggregateId(90001L)
                .businessKey("OUTBOX_IT:" + suffix + ":" + id).payload("{\"orderId\":90001}").build();
        assertThat(events.insertPending(event)).isEqualTo(1);
        return id;
    }

    private String status(String eventId) {
        return jdbc.queryForObject("SELECT status FROM outbox_event WHERE event_id=?", String.class, eventId);
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database;
    }
}
