package com.sky.auth;

import com.sky.auth.internal.application.UserServiceImpl;
import com.sky.auth.internal.persistence.UserMapper;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.WeChatProperties;
import com.sky.support.IntegrationTestBase;
import com.sky.utils.HttpClientUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "sky.outbox.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserCreationConcurrencyIT extends IntegrationTestBase {
    @Autowired private UserMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ServerEndpointExporter serverEndpointExporter;

    @Test
    void simultaneousFirstLoginsReturnOnePersistedIdentity() throws Exception {
        String openid = "race-" + UUID.randomUUID().toString();
        CyclicBarrier firstReads = new CyclicBarrier(2);
        AtomicInteger reads = new AtomicInteger();
        UserMapper controlledMapper = mock(UserMapper.class);
        when(controlledMapper.selectByOpenid(openid)).thenAnswer(invocation -> {
            User result = mapper.selectByOpenid(openid);
            if (reads.incrementAndGet() <= 2) {
                firstReads.await(10, TimeUnit.SECONDS);
            }
            return result;
        });
        doAnswer(invocation -> {
            mapper.insert(invocation.getArgument(0));
            return null;
        }).when(controlledMapper).insert(any(User.class));
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", controlledMapper);
        ReflectionTestUtils.setField(service, "weChatProperties", new WeChatProperties());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Callable<User> login = () -> {
            try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
                http.when(() -> HttpClientUtil.doGet(anyString(), anyMap()))
                        .thenReturn("{\"openid\":\"" + openid + "\"}");
                UserLoginDTO dto = new UserLoginDTO();
                dto.setCode("test-code");
                return service.wxlogin(dto);
            }
        };
        try {
            Future<User> first = workers.submit(login);
            Future<User> second = workers.submit(login);
            User firstUser = first.get(20, TimeUnit.SECONDS);
            User secondUser = second.get(20, TimeUnit.SECONDS);
            assertThat(firstUser.getId()).isNotNull().isEqualTo(secondUser.getId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE openid = ?", Integer.class, openid))
                    .isEqualTo(1);
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM user WHERE openid = ?", openid);
        }
    }
}
