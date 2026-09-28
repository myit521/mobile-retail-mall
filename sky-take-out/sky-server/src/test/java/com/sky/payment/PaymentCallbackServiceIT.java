package com.sky.payment;

import com.sky.order.api.OrderApplicationService;
import com.sky.order.internal.application.OrderServiceImpl;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.payment.api.PaymentCallbackCommand;
import com.sky.payment.api.PaymentCallbackResult;
import com.sky.payment.internal.PaymentCallbackService;
import com.sky.payment.internal.persistence.PaymentCallbackLogMapper;
import com.sky.payment.internal.persistence.PaymentEventMapper;
import com.sky.support.IntegrationTestBase;
import com.sky.observability.BusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class PaymentCallbackServiceIT extends IntegrationTestBase {
    private JdbcTemplate jdbc;
    private PaymentCallbackService service;
    private PaymentEventMapper events;
    private final AtomicBoolean failAfterEventInsert = new AtomicBoolean();
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM outbox_event WHERE aggregate_id IN (98001, 98002)");
        jdbc.update("DELETE FROM payment_callback_log WHERE out_trade_no IN ('callback-order', 'other-order')");
        jdbc.update("DELETE FROM orders WHERE id IN (98001, 98002)");
        jdbc.update("INSERT INTO orders(id, number, user_id, order_time, amount, status, pay_status) VALUES (98001, 'callback-order', 7, NOW(), 12.34, 1, 0), (98002, 'other-order', 8, NOW(), 12.34, 1, 0)");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(PaymentEventMapper.class);
        factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/OrderMapper.xml"), new ClassPathResource("mapper/PaymentCallbackLogMapper.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        DataSourceTransactionManager tm = new DataSourceTransactionManager(ds);
        OrderServiceImpl orderTarget = new OrderServiceImpl();
        ReflectionTestUtils.setField(orderTarget, "orderMapper", session.getMapper(OrderMapper.class));
        OrderApplicationService orders = (OrderApplicationService) proxy(orderTarget, tm);
        PaymentEventMapper realEvents = session.getMapper(PaymentEventMapper.class);
        events = (eventId, correlationId, aggregateId, businessKey, payload) -> {
            int affected = realEvents.insertPending(eventId, correlationId, aggregateId, businessKey, payload);
            if (failAfterEventInsert.get()) {
                throw new IllegalStateException("injected after event insert");
            }
            return affected;
        };
        PaymentCallbackService target = new PaymentCallbackService(session.getMapper(PaymentCallbackLogMapper.class), orders, events);
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(target, "metrics", new BusinessMetrics(meterRegistry));
        service = (PaymentCallbackService) proxy(target, tm);
    }

    @Test
    void tripleDeliveryTransitionsAndPersistsExactlyOnce() {
        assertThat(service.handle(command())).isEqualTo(PaymentCallbackResult.APPLIED);
        assertThat(service.handle(command())).isEqualTo(PaymentCallbackResult.DUPLICATE);
        assertThat(service.handle(command())).isEqualTo(PaymentCallbackResult.DUPLICATE);
        assertAppliedOnce();
        assertThat(meterRegistry.get("sky.payment.callback.duplicates").counter().count()).isEqualTo(2);
    }

    @Test
    void concurrentDeliveriesHaveOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<PaymentCallbackResult> call = () -> { ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return service.handle(command()); };
            List<Future<PaymentCallbackResult>> futures = List.of(pool.submit(call), pool.submit(call), pool.submit(call));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS), futures.get(2).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(PaymentCallbackResult.APPLIED, PaymentCallbackResult.DUPLICATE, PaymentCallbackResult.DUPLICATE);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertAppliedOnce();
    }

    @Test
    void eventFailureRollsBackCallbackOrderAndEventThenRetrySucceeds() {
        failAfterEventInsert.set(true);
        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(IllegalStateException.class);
        assertUnchanged();
        failAfterEventInsert.set(false);
        assertThat(service.handle(command())).isEqualTo(PaymentCallbackResult.APPLIED);
        assertAppliedOnce();
    }

    @Test
    void amountMismatchAndCancelledOrderLeaveNoCallbackOrEvent() {
        assertThatThrownBy(() -> service.handle(new PaymentCallbackCommand("callback-order", "wx-98001", 1233))).isInstanceOf(RuntimeException.class);
        assertUnchanged();
        jdbc.update("UPDATE orders SET status = 6 WHERE id = 98001");
        assertThatThrownBy(() -> service.handle(command())).isInstanceOf(RuntimeException.class);
        assertThat(count("payment_callback_log")).isZero();
        assertThat(count("outbox_event")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=98001", Integer.class)).isEqualTo(6);
    }

    @Test
    void transactionIdCannotBeReboundToAnotherOrder() {
        service.handle(command());
        assertThatThrownBy(() -> service.handle(new PaymentCallbackCommand("other-order", "wx-98001", 1234))).isInstanceOf(RuntimeException.class);
        assertAppliedOnce();
        assertThat(jdbc.queryForObject("SELECT pay_status FROM orders WHERE id=98002", Integer.class)).isZero();
    }

    private void assertAppliedOnce() {
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=98001", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT pay_status FROM orders WHERE id=98001", Integer.class)).isEqualTo(1);
        assertThat(count("payment_callback_log")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event WHERE aggregate_id=98001", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_callback_log WHERE out_trade_no='callback-order' AND raw_callback_data IS NULL AND decrypted_data IS NULL", Integer.class)).isEqualTo(1);
    }

    private void assertUnchanged() {
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=98001", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT pay_status FROM orders WHERE id=98001", Integer.class)).isZero();
        assertThat(count("payment_callback_log")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    private int count(String table) {
        String where = table.equals("outbox_event") ? "aggregate_id IN (98001,98002)" : "out_trade_no IN ('callback-order','other-order')";
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
    }

    private PaymentCallbackCommand command() { return new PaymentCallbackCommand("callback-order", "wx-98001", 1234); }

    private Object proxy(Object target, DataSourceTransactionManager tm) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return factory.getProxy();
    }
}
