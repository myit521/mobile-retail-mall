package com.sky.observability;

import com.sky.messaging.outbox.OutboxEventMapper;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
@SpringBootTest(classes = ActuatorMetricsIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=127.0.0.1",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.show-components=never",
        "management.endpoint.health.group.liveness.include=livenessState",
        "management.endpoint.health.group.readiness.include=readinessState,db,rabbit"
        ,"spring.flyway.enabled=false"
        ,"spring.mvc.pathmatch.matching-strategy=ant_path_matcher"
        ,"spring.autoconfigure.exclude=com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure,org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,springfox.boot.starter.autoconfigure.OpenApiAutoConfiguration"
})
@AutoConfigureMetrics
class ActuatorMetricsIT {
    @Autowired
    private TestRestTemplate http;
    @LocalManagementPort
    private int managementPort;

    @Test
    void isolatedEndpointSerializationHidesDependencyDetails() throws Exception {
        String base = "http://127.0.0.1:" + managementPort;
        assertThat(http.getForEntity(base + "/actuator/health/liveness", String.class).getStatusCodeValue()).isEqualTo(200);
        org.springframework.http.ResponseEntity<String> readiness = http.getForEntity(base + "/actuator/health/readiness", String.class);
        assertThat(readiness.getStatusCodeValue()).isEqualTo(503);
        assertThat(readiness.getBody()).contains("\"status\":\"DOWN\"").doesNotContain("rabbit-secret", "components");
        assertThat(http.getForEntity(base + "/actuator/prometheus", String.class).getStatusCodeValue()).isEqualTo(200);
        assertThat(http.getForEntity(base + "/actuator/env", String.class).getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void productionYamlBindsSeparateManagementSurfaceAndProbeGroups() throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        PropertySourcesPropertyResolver properties = new PropertySourcesPropertyResolver(sources);

        assertThat(properties.getProperty("management.server.port", Integer.class)).isEqualTo(8081);
        assertThat(properties.getProperty("management.server.address")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,prometheus");
        assertThat(properties.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(properties.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db,rabbit");
    }
    @Test
    void businessMetersUseOnlyBoundedDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry);

        metrics.orderConflict(BusinessMetrics.OrderOperation.PAYMENT);
        metrics.stockFailure(BusinessMetrics.StockOperation.DEDUCT);
        metrics.duplicateCallback();
        metrics.deadLetter(BusinessMetrics.Consumer.ORDER_PAID_NOTIFY);

        assertThat(registry.getMeters()).extracting(Meter::getId).allSatisfy(id -> {
            assertThat(id.getTags()).allSatisfy(tag -> {
                assertThat(tag.getKey()).isIn("operation", "consumer");
                assertThat(tag.getValue()).matches("[a-z_]+");
            });
            assertThat(id.getTags()).noneMatch(tag -> Set.of("orderId", "userId", "eventId", "error").contains(tag.getKey()));
        });
    }

    @Test
    void outboxHealthUsesIndexedCountsAndExternalThresholds() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        when(mapper.countByStatus("PENDING")).thenReturn(3L);
        when(mapper.countByStatus("FAILED")).thenReturn(0L);
        OutboxHealthIndicator indicator = new OutboxHealthIndicator(mapper, 10, 0);
        indicator.refreshCounts();

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("pending", 3L).containsEntry("failed", 0L);
    }

    @Test
    void outboxHealthReadsCacheAndTurnsDownOnStartupOrRefreshFailure() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        OutboxHealthIndicator indicator = new OutboxHealthIndicator(mapper, 10, 0);
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        when(mapper.countByStatus("PENDING")).thenReturn(1L);
        when(mapper.countByStatus("FAILED")).thenReturn(0L);
        indicator.refreshCounts();
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        when(mapper.countByStatus("PENDING")).thenThrow(new IllegalStateException("db unavailable"));
        indicator.refreshCounts();
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(2)).countByStatus("PENDING");
    }

    @Test
    void outboxBacklogNeverParticipatesInLivenessAndHealthDetailsStayHidden() throws Exception {
        String yaml = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("port: ${MANAGEMENT_SERVER_PORT:8081}");
        assertThat(yaml).contains("address: ${MANAGEMENT_SERVER_ADDRESS:127.0.0.1}");
        assertThat(yaml).contains("include: livenessState");
        assertThat(yaml).contains("include: readinessState,db,rabbit");
        assertThat(yaml).contains("show-details: never");
        assertThat(yaml).contains("include: health,prometheus");
        assertThat(yaml).doesNotContain("include: livenessState,outbox");
        assertThat(yaml).doesNotContain("stale-after");
    }

    // Remain an explicit test source without becoming another discoverable application root.
    @Configuration
    @TestComponent
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, RabbitAutoConfiguration.class})
    static class TestApplication {
        @Bean(name = "dbHealthIndicator")
        HealthIndicator dbHealthIndicator() {
            return () -> Health.up().build();
        }

        @Bean(name = "rabbitHealthIndicator")
        HealthIndicator rabbitHealthIndicator() {
            return () -> Health.down().withDetail("credential", "rabbit-secret").build();
        }
    }
}
