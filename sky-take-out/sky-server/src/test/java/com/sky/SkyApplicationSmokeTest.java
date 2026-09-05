package com.sky;

import com.sky.support.IntegrationTestBase;
import com.sky.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SkyApplicationSmokeTest extends IntegrationTestBase {

    @MockBean
    private ServerEndpointExporter serverEndpointExporter;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void productionMiddlewareRoundTrip() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }

        String redisKey = "smoke:" + TestDataFactory.nextEventId();
        try {
            stringRedisTemplate.opsForValue().set(redisKey, "ready");
            assertThat(stringRedisTemplate.opsForValue().get(redisKey)).isEqualTo("ready");
        } finally {
            stringRedisTemplate.delete(redisKey);
        }

        String queue = "sky.integration.smoke." + TestDataFactory.nextEventId();
        try {
            rabbitTemplate.execute(channel -> {
                channel.queueDeclare(queue, false, true, true, null);
                return null;
            });
            rabbitTemplate.convertAndSend("", queue, "ready");
            assertThat(rabbitTemplate.receiveAndConvert(queue, 5_000)).isEqualTo("ready");
        } finally {
            rabbitTemplate.execute(channel -> {
                channel.queueDelete(queue);
                return null;
            });
        }
    }
}
