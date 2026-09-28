package com.sky.inventory;

import com.sky.entity.OrderDetail;
import com.sky.exception.BaseException;
import com.sky.inventory.api.InventoryService;
import com.sky.inventory.internal.application.StockServiceImpl;
import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockLogMapper;
import com.sky.support.IntegrationTestBase;
import com.sky.observability.BusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockConcurrencyIT extends IntegrationTestBase {
    private static final long LAST_ITEM_PRODUCT_ID = 97001L;
    private static final long RELEASE_PRODUCT_ID = 97002L;

    private InventoryProductMapper productMapper;
    private InventoryService inventoryService;
    private StockServiceImpl stockService;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM stock_log WHERE order_id BETWEEN 97001 AND 97010");
        jdbc.update("DELETE FROM stock_operation WHERE order_id BETWEEN 97001 AND 97010");
        jdbc.update("DELETE FROM product WHERE id IN (?, ?)", LAST_ITEM_PRODUCT_ID, RELEASE_PRODUCT_ID);
        jdbc.update("INSERT INTO product (id, name, category_id, price, stock) VALUES (?, ?, 1, 1.00, 1)",
                LAST_ITEM_PRODUCT_ID, "last-item");
        jdbc.update("INSERT INTO product (id, name, category_id, price, stock) VALUES (?, ?, 1, 1.00, 0)",
                RELEASE_PRODUCT_ID, "released-item");

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(InventoryProductMapper.class);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/StockLogMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        SqlSessionTemplate session = new SqlSessionTemplate(sessionFactory);
        productMapper = session.getMapper(InventoryProductMapper.class);
        StockLogMapper stockLogMapper = session.getMapper(StockLogMapper.class);

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(20);

        stockService = new StockServiceImpl();
        ReflectionTestUtils.setField(stockService, "productMapper", productMapper);
        ReflectionTestUtils.setField(stockService, "stockLogMapper", stockLogMapper);
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(stockService, "metrics", new BusinessMetrics(meterRegistry));
        ProxyFactory proxyFactory = new ProxyFactory(stockService);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        inventoryService = (InventoryService) proxyFactory.getProxy();
    }

    @Test
    void twoSimultaneousLastItemDeductionsHaveOneWinnerAndNeverGoNegative() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> attempts = List.of(
                    executor.submit(() -> deductAfterRelease(97005L, ready, start)),
                    executor.submit(() -> deductAfterRelease(97006L, ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> affectedRows = new ArrayList<>();
            for (Future<Integer> attempt : attempts) {
                affectedRows.add(attempt.get(20, TimeUnit.SECONDS));
            }
            assertThat(affectedRows).containsExactlyInAnyOrder(1, 0);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isZero();
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isNotNegative();
        assertThat(meterRegistry.get("sky.stock.failures").tag("operation", "deduct")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void threeSimultaneousOrderClosedReleasesIncrementAndAuditOnce() throws Exception {
        long orderId = 97001L;
        OrderDetail detail = detail(RELEASE_PRODUCT_ID, 1);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> attempts = List.of(
                    executor.submit(() -> releaseAfterSignal(orderId, detail, ready, start)),
                    executor.submit(() -> releaseAfterSignal(orderId, detail, ready, start)),
                    executor.submit(() -> releaseAfterSignal(orderId, detail, ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(stockOf(RELEASE_PRODUCT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_operation WHERE order_id = ? AND action_key = 'ORDER_CLOSED'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT quantity FROM stock_operation WHERE order_id = ? AND action_key = 'ORDER_CLOSED'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT outcome FROM stock_operation WHERE order_id = ? AND action_key = 'ORDER_CLOSED'",
                String.class, orderId)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_log WHERE order_id = ? AND change_type = 3",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void failedReleaseRollsBackGuardSoTheSameActionCanBeRetried() {
        long orderId = 97002L;
        jdbc.update("UPDATE product SET stock = ? WHERE id = ?", Integer.MAX_VALUE, RELEASE_PRODUCT_ID);

        assertThatThrownBy(() -> inventoryService.returnStock(
                orderId, List.of(detail(RELEASE_PRODUCT_ID, 1))))
                .isInstanceOf(RuntimeException.class);
        assertThat(stockOf(RELEASE_PRODUCT_ID)).isEqualTo(Integer.MAX_VALUE);
        assertThat(operationCount(orderId)).isZero();

        jdbc.update("UPDATE product SET stock = 0 WHERE id = ?", RELEASE_PRODUCT_ID);
        inventoryService.returnStock(orderId, List.of(detail(RELEASE_PRODUCT_ID, 1)));
        assertThat(stockOf(RELEASE_PRODUCT_ID)).isEqualTo(1);
        assertThat(operationCount(orderId)).isEqualTo(1);
    }

    @Test
    void aggregateReleaseQuantityExcludesMissingProductsThatAreSkipped() {
        long orderId = 97003L;
        inventoryService.returnStock(orderId, List.of(
                detail(RELEASE_PRODUCT_ID, 2), detail(97999L, 7)));

        assertThat(stockOf(RELEASE_PRODUCT_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT quantity FROM stock_operation WHERE order_id = ? AND action_key = 'ORDER_CLOSED'",
                Integer.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_log WHERE order_id = ? AND change_type = 3",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void nonPositiveQuantitiesCannotIncreaseOrReleaseStock() {
        assertThat(productMapper.deductIfAvailable(LAST_ITEM_PRODUCT_ID, -1)).isZero();
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(1);
        assertThatThrownBy(() -> inventoryService.returnStock(
                97004L, List.of(detail(RELEASE_PRODUCT_ID, 0))))
                .isInstanceOf(BaseException.class);
        assertThat(stockOf(RELEASE_PRODUCT_ID)).isZero();
        assertThat(operationCount(97004L)).isZero();
    }

    private int deductAfterRelease(long orderId, CountDownLatch ready, CountDownLatch start) {
        return transaction.execute(status -> {
            ready.countDown();
            await(start);
            try {
                stockService.deductStock(orderId, List.of(detail(LAST_ITEM_PRODUCT_ID, 1)));
                return 1;
            } catch (BaseException exception) {
                return 0;
            }
        });
    }

    private void releaseAfterSignal(long orderId, OrderDetail detail, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        inventoryService.returnStock(orderId, List.of(detail));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent test start", exception);
        }
    }

    private OrderDetail detail(long productId, int quantity) {
        return OrderDetail.builder().productId(productId).number(quantity).build();
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
    }

    private int operationCount(long orderId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_operation WHERE order_id = ? AND action_key = 'ORDER_CLOSED'",
                Integer.class, orderId);
    }
}
