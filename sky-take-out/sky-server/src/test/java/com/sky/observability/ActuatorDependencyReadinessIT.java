package com.sky.observability;

import com.sky.SkyApplication;
import com.sky.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SkyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ActuatorDependencyReadinessIT extends IntegrationTestBase {
    @LocalServerPort int applicationPort;
    @LocalManagementPort int managementPort;
    @Autowired TestRestTemplate http;
    @MockBean ServerEndpointExporter serverEndpointExporter;

    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> 0);
        registry.add("management.server.address", () -> "127.0.0.1");
    }

    @Test
    void productionContextKeepsMetricsOffBusinessPortAndTracksRealRabbitReadiness() throws Exception {
        assertThat(applicationPort).isNotEqualTo(managementPort);
        assertThat(http.getForEntity("http://127.0.0.1:" + applicationPort + "/actuator/prometheus", String.class)
                .getStatusCodeValue()).isEqualTo(404);
        assertThat(http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/prometheus", String.class)
                .getStatusCodeValue()).isEqualTo(200);
        assertThat(readiness().getBody()).contains("\"status\":\"UP\"");

        try {
            // Closing broker connections avoids reading cached handshake data from an open connection.
            assertThat(RABBITMQ.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
            while (readiness().getStatusCodeValue() != 503 && System.nanoTime() < deadline) {
                Thread.sleep(250);
            }
            assertThat(readiness().getStatusCodeValue()).isEqualTo(503);
            assertThat(http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health/liveness", String.class)
                    .getStatusCodeValue()).isEqualTo(200);
        } finally {
            assertThat(RABBITMQ.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
        }
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (readiness().getStatusCodeValue() != 200 && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
        assertThat(readiness().getStatusCodeValue()).isEqualTo(200);
    }

    private ResponseEntity<String> readiness() {
        return http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health/readiness", String.class);
    }
}
