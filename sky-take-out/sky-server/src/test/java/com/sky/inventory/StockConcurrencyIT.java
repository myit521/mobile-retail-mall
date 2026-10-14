package com.sky.inventory;

import com.sky.entity.OrderDetail;
import com.sky.entity.Product;
import com.sky.dto.StockAdjustDTO;
import com.sky.dto.StockCheckRecordDTO;
import com.sky.entity.StockCheckRecord;
import com.sky.exception.BaseException;
import com.sky.inventory.api.InventoryService;
import com.sky.inventory.internal.application.StockServiceImpl;
import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockLogMapper;
import com.sky.inventory.internal.persistence.StockCheckRecordMapper;
import com.sky.inventory.internal.persistence.StockCheckPlanMapper;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class StockConcurrencyIT extends IntegrationTestBase {
    private static final long LAST_ITEM_PRODUCT_ID = 97001L;
    private static final long RELEASE_PRODUCT_ID = 97002L;

    private InventoryProductMapper productMapper;
    private com.sky.product.internal.persistence.ProductMapper catalogMapper;
    private StockCheckRecordMapper checkRecords;
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
        jdbc.update("DELETE FROM stock_log WHERE order_id BETWEEN 97001 AND 97010 OR product_id IN (?, ?)",
                LAST_ITEM_PRODUCT_ID, RELEASE_PRODUCT_ID);
        jdbc.update("DELETE FROM stock_check_record WHERE product_id IN (?, ?)", LAST_ITEM_PRODUCT_ID, RELEASE_PRODUCT_ID);
        jdbc.update("DELETE FROM stock_check_plan WHERE id = 97001");
        jdbc.update("INSERT INTO stock_check_plan (id, plan_name, status, create_user, create_time) "
                + "VALUES (97001, 'duplicate-check-test', 0, 1, NOW())");
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
        configuration.addMapper(com.sky.product.internal.persistence.ProductMapper.class);
        configuration.addMapper(StockCheckRecordMapper.class);
        configuration.addMapper(StockCheckPlanMapper.class);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/StockLogMapper.xml"),
                new ClassPathResource("mapper/ProductMapper.xml"),
                new ClassPathResource("mapper/StockCheckPlanMapper.xml"),
                new ClassPathResource("mapper/StockCheckRecordMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        SqlSessionTemplate session = new SqlSessionTemplate(sessionFactory);
        productMapper = session.getMapper(InventoryProductMapper.class);
        catalogMapper = session.getMapper(com.sky.product.internal.persistence.ProductMapper.class);
        checkRecords = session.getMapper(StockCheckRecordMapper.class);
        StockLogMapper stockLogMapper = session.getMapper(StockLogMapper.class);

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(20);

        stockService = new StockServiceImpl();
        ReflectionTestUtils.setField(stockService, "productMapper", productMapper);
        ReflectionTestUtils.setField(stockService, "stockLogMapper", stockLogMapper);
        ReflectionTestUtils.setField(stockService, "stockCheckRecordMapper", checkRecords);
        ReflectionTestUtils.setField(stockService, "stockCheckPlanMapper", session.getMapper(StockCheckPlanMapper.class));
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(stockService, "metrics", new BusinessMetrics(meterRegistry));
        ProxyFactory proxyFactory = new ProxyFactory(stockService);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        inventoryService = (InventoryService) proxyFactory.getProxy();
    }

    @Test
    void productEditWithStaleStockPreservesCommittedInventory() {
        Product staleEdit = Product.builder().id(LAST_ITEM_PRODUCT_ID).name("renamed-item").stock(1).build();
        jdbc.update("UPDATE product SET stock = 8 WHERE id = ?", LAST_ITEM_PRODUCT_ID);

        catalogMapper.updateProduct(staleEdit);

        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT name FROM product WHERE id = ?", String.class,
                LAST_ITEM_PRODUCT_ID)).isEqualTo("renamed-item");
    }

    @Test
    void deductionAuditContinuesCommittedAdjustmentChain() throws Exception {
        assertConcurrentAuditChain(false);
    }

    @Test
    void returnAuditContinuesCommittedAdjustmentChain() throws Exception {
        assertConcurrentAuditChain(true);
    }

    private void assertConcurrentAuditChain(boolean returning) throws Exception {
        jdbc.update("UPDATE product SET stock = 10 WHERE id = ?", LAST_ITEM_PRODUCT_ID);
        CountDownLatch readingStock = new CountDownLatch(1);
        InventoryProductMapper racingProducts = mock(InventoryProductMapper.class, delegatesTo(productMapper));
        doAnswer(invocation -> {
            Product snapshot = productMapper.selectProductById(LAST_ITEM_PRODUCT_ID);
            readingStock.countDown();
            return snapshot;
        }).when(racingProducts).selectProductById(LAST_ITEM_PRODUCT_ID);
        doAnswer(invocation -> {
            readingStock.countDown();
            return productMapper.selectProductForUpdate(LAST_ITEM_PRODUCT_ID);
        }).when(racingProducts).selectProductForUpdate(LAST_ITEM_PRODUCT_ID);
        ReflectionTestUtils.setField(stockService, "productMapper", racingProducts);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> operation = transaction.execute(status -> {
                productMapper.selectProductForUpdate(LAST_ITEM_PRODUCT_ID);
                Future<?> attempt = executor.submit(() -> transaction.execute(workerStatus -> {
                    if (returning) {
                        stockService.returnStock(97008L, List.of(detail(LAST_ITEM_PRODUCT_ID, 3)));
                    } else {
                        stockService.deductStock(97008L, List.of(detail(LAST_ITEM_PRODUCT_ID, 3)));
                    }
                    return null;
                }));
                await(readingStock);
                StockAdjustDTO adjustment = new StockAdjustDTO();
                adjustment.setProductId(LAST_ITEM_PRODUCT_ID);
                adjustment.setQuantity(5);
                stockService.adjustStock(adjustment);
                return attempt;
            });
            operation.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        int finalStock = returning ? 18 : 12;
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(finalStock);
        List<int[]> chain = jdbc.query("SELECT before_stock, after_stock, change_quantity FROM stock_log "
                        + "WHERE product_id = ? ORDER BY id", (rs, row) -> new int[] {
                        rs.getInt("before_stock"), rs.getInt("after_stock"), rs.getInt("change_quantity") },
                LAST_ITEM_PRODUCT_ID);
        assertThat(chain).hasSize(2);
        assertThat(chain.get(0)).containsExactly(10, 15, 5);
        assertThat(chain.get(1)).containsExactly(15, finalStock, returning ? 3 : -3);
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

    @Test
    void repeatedCheckSubmissionIsRejectedBeforeAndAfterConfirmation() {
        jdbc.update("UPDATE product SET stock = 10 WHERE id = ?", LAST_ITEM_PRODUCT_ID);
        StockCheckRecordDTO request = checkRequest();
        transaction.execute(status -> { stockService.submitCheckRecord(request); return null; });
        assertThatThrownBy(() -> transaction.execute(status -> {
            stockService.submitCheckRecord(request);
            return null;
        })).isInstanceOf(BaseException.class);
        long recordId = jdbc.queryForObject("SELECT id FROM stock_check_record WHERE plan_id=97001 AND product_id=?",
                Long.class, LAST_ITEM_PRODUCT_ID);
        transaction.execute(status -> { stockService.confirmCheckRecord(recordId); return null; });
        assertThatThrownBy(() -> transaction.execute(status -> {
            stockService.submitCheckRecord(request);
            return null;
        })).isInstanceOf(BaseException.class);
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(12);
    }

    @Test
    void concurrentCheckSubmissionsCreateOneDifferenceRecord() throws Exception {
        jdbc.update("UPDATE product SET stock = 10 WHERE id = ?", LAST_ITEM_PRODUCT_ID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> submit = () -> {
                ready.countDown();
                await(start);
                try {
                    transaction.execute(status -> { stockService.submitCheckRecord(checkRequest()); return null; });
                    return true;
                } catch (BaseException duplicate) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(submit);
            Future<Boolean> second = executor.submit(submit);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_check_record WHERE plan_id=97001 AND product_id=?",
                Integer.class, LAST_ITEM_PRODUCT_ID)).isEqualTo(1);
    }

    private StockCheckRecordDTO checkRequest() {
        StockCheckRecordDTO request = new StockCheckRecordDTO();
        request.setPlanId(97001L);
        request.setProductId(LAST_ITEM_PRODUCT_ID);
        request.setActualStock(12);
        return request;
    }

    @Test
    void manualAdjustmentAndOrderDeductionBothSurviveConcurrentCommit() throws Exception {
        jdbc.update("UPDATE product SET stock = 10 WHERE id = ?", LAST_ITEM_PRODUCT_ID);
        StockAdjustDTO adjustment = new StockAdjustDTO();
        adjustment.setProductId(LAST_ITEM_PRODUCT_ID);
        adjustment.setQuantity(5);
        adjustment.setRemark("concurrent adjustment");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> adjusted = executor.submit(() -> transaction.execute(status -> {
                ready.countDown();
                await(start);
                stockService.adjustStock(adjustment);
                return null;
            }));
            Future<?> deducted = executor.submit(() -> transaction.execute(status -> {
                ready.countDown();
                await(start);
                stockService.deductStock(97007L, List.of(detail(LAST_ITEM_PRODUCT_ID, 3)));
                return null;
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            adjusted.get(20, TimeUnit.SECONDS);
            deducted.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_log WHERE product_id = ?",
                Integer.class, LAST_ITEM_PRODUCT_ID)).isEqualTo(2);
    }

    @Test
    void simultaneousConfirmationsApplyCheckDifferenceAndAuditOnlyOnce() throws Exception {
        jdbc.update("UPDATE product SET stock = 8 WHERE id = ?", LAST_ITEM_PRODUCT_ID);
        jdbc.update("INSERT INTO stock_check_record "
                + "(id, plan_id, product_id, system_stock, actual_stock, diff_quantity, status, create_time) "
                + "VALUES (97001, 97001, ?, 10, 13, 3, 0, NOW())", LAST_ITEM_PRODUCT_ID);
        CyclicBarrier bothReadPending = new CyclicBarrier(2);
        StockCheckRecordMapper racingRecords = mock(StockCheckRecordMapper.class, delegatesTo(checkRecords));
        doAnswer(invocation -> {
            StockCheckRecord record = checkRecords.selectById(97001L);
            bothReadPending.await(10, TimeUnit.SECONDS);
            return record;
        }).when(racingRecords).selectById(97001L);
        ReflectionTestUtils.setField(stockService, "stockCheckRecordMapper", racingRecords);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(this::confirmRecord);
            Future<Boolean> second = executor.submit(this::confirmRecord);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(11);
        assertThat(checkRecords.selectById(97001L).getStatus()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_log WHERE product_id = ? AND change_type = 5",
                Integer.class, LAST_ITEM_PRODUCT_ID)).isEqualTo(1);
    }

    @Test
    void databaseRejectsNegativeInventoryAndCheckValues() {
        assertThatThrownBy(() -> jdbc.update("UPDATE product SET stock = -1 WHERE id = ?", LAST_ITEM_PRODUCT_ID))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("chk_product_stock_nonnegative");
        assertThat(stockOf(LAST_ITEM_PRODUCT_ID)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO stock_check_record "
                + "(id, plan_id, product_id, system_stock, actual_stock, diff_quantity, status, create_time) "
                + "VALUES (97001, 97001, ?, 1, -1, -2, 0, NOW())", LAST_ITEM_PRODUCT_ID))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("chk_check_actual_stock_nonnegative");
    }

    private boolean confirmRecord() {
        try {
            transaction.execute(status -> {
                stockService.confirmCheckRecord(97001L);
                return null;
            });
            return true;
        } catch (BaseException alreadyConfirmed) {
            return false;
        }
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
