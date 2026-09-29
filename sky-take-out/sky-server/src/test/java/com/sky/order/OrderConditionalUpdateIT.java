package com.sky.order;

import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.inventory.api.InventoryService;
import com.sky.order.internal.application.OrderServiceImpl;
import com.sky.order.internal.persistence.OrderDetailMapper;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.support.IntegrationTestBase;
import com.sky.utils.WeChatPayUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderConditionalUpdateIT extends IntegrationTestBase {
    private OrderMapper mapper;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM orders WHERE id IN (90001, 90002)");
        jdbc.update("INSERT INTO orders (id, number, status, user_id, order_time, pay_status, amount) "
                + "VALUES (90001, 'transition-race', 2, 10, NOW(), 1, 12.50), "
                + "(90002, 'foreign-owner', 2, 11, NOW(), 1, 12.50)");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/OrderMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        mapper = new SqlSessionTemplate(sessionFactory).getMapper(OrderMapper.class);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(20);
    }

    @Test
    void twoSynchronizedCancelTransactionsHaveOneWinnerAndIdempotentLoser() throws Exception {
        CyclicBarrier bothReadPending = new CyclicBarrier(2);
        List<Integer> affectedRows = new CopyOnWriteArrayList<>();
        OrderMapper racingMapper = mock(OrderMapper.class, delegatesTo(mapper));
        doAnswer(invocation -> {
            Orders order = mapper.selectByIdAndUserId(90001L, 10L);
            bothReadPending.await(10, TimeUnit.SECONDS);
            return order;
        }).when(racingMapper).selectByIdAndUserId(90001L, 10L);
        doAnswer(invocation -> {
            int changed = mapper.transition(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
            affectedRows.add(changed);
            return changed;
        }).when(racingMapper).transition(eq(90001L), eq(10L), eq(2), eq(6), any());
        InventoryService inventory = mock(InventoryService.class);
        WeChatPayUtil payment = mock(WeChatPayUtil.class);
        OrderServiceImpl service = service(racingMapper, inventory, payment);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> cancel(service));
            Future<Integer> second = executor.submit(() -> cancel(service));
            assertThat(first.get(25, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(25, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(affectedRows).containsExactlyInAnyOrder(1, 0);
        Orders cancelled = mapper.selectbyId(90001L);
        assertThat(cancelled.getStatus()).isEqualTo(6);
        assertThat(cancelled.getPayStatus()).isEqualTo(2);
        assertThat(cancelled.getCancelTime()).isNotNull();
        assertThat(cancelled.getCancelReason()).isEqualTo("用户取消订单");
        verify(payment, times(1)).refund(eq("transition-race"), anyString(), any(), any());
        verify(inventory, times(1)).returnStock(eq(90001L), anyList());
        verify(racingMapper, times(1)).selectForUpdate(90001L, 10L);
    }

    @Test
    void foreignOwnedOrderCannotChangeOrBeReportedAsIdempotent() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 9, 7, 12, 0);
        assertThat(transaction.<Integer>execute(status -> mapper.transition(90002L, 10L, 2, 6, changedAt))).isZero();
        Orders foreign = mapper.selectbyId(90002L);
        assertThat(foreign.getStatus()).isEqualTo(2);
        assertThat(foreign.getPayStatus()).isEqualTo(1);
        assertThat(foreign.getCancelTime()).isNull();
        InventoryService inventory = mock(InventoryService.class);
        WeChatPayUtil payment = mock(WeChatPayUtil.class);
        OrderServiceImpl service = service(mapper, inventory, payment);
        BaseContext.setCurrentId(10L);
        try {
            assertThatThrownBy(() -> transaction.execute(status -> service.userCancel(90002L)))
                    .isInstanceOf(OrderBusinessException.class);
            jdbc.update("UPDATE orders SET status = 6 WHERE id = 90002");
            assertThat(transaction.<Orders>execute(status -> mapper.selectForUpdate(90002L, 10L))).isNull();
            assertThatThrownBy(() -> transaction.execute(status -> service.userCancel(90002L)))
                    .isInstanceOf(OrderBusinessException.class);
        } finally {
            BaseContext.clear();
        }
        verifyNoInteractions(inventory, payment);
    }

    @Test
    void staleExpectedStatusCannotOverwriteAndAdminTransitionRecordsTime() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 9, 7, 12, 0);
        assertThat(transaction.<Integer>execute(status -> mapper.transition(90001L, null, 1, 6, changedAt))).isZero();
        assertThat(mapper.selectbyId(90001L).getStatus()).isEqualTo(2);
        assertThat(transaction.<Integer>execute(status -> mapper.transition(90001L, null, 2, 6, changedAt))).isEqualTo(1);
        assertThat(mapper.selectbyId(90001L).getCancelTime()).isEqualTo(changedAt);
    }

    private Integer cancel(OrderServiceImpl service) {
        BaseContext.setCurrentId(10L);
        try {
            return transaction.execute(status -> service.userCancel(90001L).getCode());
        } finally {
            BaseContext.clear();
        }
    }

    private OrderServiceImpl service(OrderMapper orderMapper, InventoryService inventory, WeChatPayUtil payment) {
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", mock(OrderDetailMapper.class));
        ReflectionTestUtils.setField(service, "inventoryService", inventory);
        ReflectionTestUtils.setField(service, "weChatPayUtil", payment);
        return service;
    }
}
