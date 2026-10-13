package com.sky.order;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.exception.OrderBusinessException;
import com.sky.entity.OrderDetail;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.internal.messaging.OrderEventPublisher;
import com.sky.product.api.ShoppingCartService;
import com.sky.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "sky.outbox.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderSubmissionConcurrencyIT extends IntegrationTestBase {
    private static final long USER_ID = 99001L;
    private static final long PRODUCT_ID = 99001L;

    @Autowired private OrderApplicationService orders;
    @Autowired private ShoppingCartService cart;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private OrderEventPublisher events;
    @MockBean private ServerEndpointExporter serverEndpointExporter;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM order_detail WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", USER_ID);
        jdbc.update("DELETE FROM orders WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM stock_log WHERE product_id = ?", PRODUCT_ID);
        jdbc.update("DELETE FROM shopping_cart WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM product WHERE id = ?", PRODUCT_ID);
        jdbc.update("DELETE FROM user WHERE id = ?", USER_ID);
        jdbc.update("INSERT INTO user (id, openid) VALUES (?, 'concurrent-cart-user')", USER_ID);
        jdbc.update("INSERT INTO product (id, name, category_id, price, stock, status) "
                + "VALUES (?, 'concurrent-cart-product', 1, 2.50, 10, 1)", PRODUCT_ID);
        jdbc.update("INSERT INTO shopping_cart (user_id, product_id, number, amount, create_time) "
                + "VALUES (?, ?, 1, 2.50, NOW())", USER_ID, PRODUCT_ID);
    }

    @Test
    void overlappingSubmissionsConsumeTheCartAndDeductStockOnlyOnce() throws Exception {
        runDuringSubmission(() -> assertThatThrownBy(() -> orders.submit(request()))
                .isInstanceOf(OrderBusinessException.class));

        assertSubmittedOnce();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id = ?",
                Integer.class, USER_ID)).isZero();
    }

    @Test
    void cartAdditionDuringSubmissionSurvivesAfterTheOrderCommits() throws Exception {
        ShoppingCartDTO item = new ShoppingCartDTO();
        item.setProductId(PRODUCT_ID);
        runDuringSubmission(() -> cart.add(item));

        assertSubmittedOnce();
        assertThat(jdbc.queryForObject("SELECT SUM(number) FROM shopping_cart WHERE user_id = ?",
                Integer.class, USER_ID)).isEqualTo(1);
    }

    @Test
    void repetitionRestoresItemsEvenWhenItsTransactionSnapshotPredatesSubmissionCommit() throws Exception {
        jdbc.update("INSERT INTO orders (id, number, user_id, status, pay_status, order_time, amount) "
                + "VALUES (99002, 'historical-cart-order', ?, 5, 1, NOW(), 5.00)", USER_ID);
        jdbc.update("INSERT INTO order_detail (order_id, product_id, number, amount) VALUES (99002, ?, 2, 2.50)", PRODUCT_ID);
        CountDownLatch snapshotRead = new CountDownLatch(1);
        runDuringSubmission(() -> new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE user_id = ?",
                    Integer.class, USER_ID)).isEqualTo(1);
            snapshotRead.countDown();
            orders.repetition(99002L);
            return null;
        }), snapshotRead);

        assertThat(jdbc.queryForObject("SELECT SUM(number) FROM shopping_cart WHERE user_id = ?",
                Integer.class, USER_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, PRODUCT_ID))
                .isEqualTo(9);
    }

    @Test
    void refreshedCartUsesChangedProductPriceAndCanBeSubmitted() {
        jdbc.update("UPDATE product SET price = 3.00 WHERE id = ?", PRODUCT_ID);
        asUser(() -> {
            assertThat(cart.list().get(0).getAmount()).isEqualByComparingTo("3.00");
            OrdersSubmitDTO refreshed = request();
            refreshed.setAmount(cart.list().get(0).getAmount());
            assertThat(orders.submit(refreshed).getOrderAmount()).isEqualByComparingTo("3.00");
        });
        assertSubmittedOnce();
    }

    @Test
    void historicalItemsDisplayCurrentPricesAfterRestoration() {
        jdbc.update("DELETE FROM shopping_cart WHERE user_id = ?", USER_ID);
        jdbc.update("UPDATE product SET price = 3.00 WHERE id = ?", PRODUCT_ID);
        asUser(() -> {
            cart.restore(List.of(OrderDetail.builder().productId(PRODUCT_ID).number(2)
                    .amount(new BigDecimal("2.50")).build()));
            assertThat(cart.list().get(0).getAmount()).isEqualByComparingTo("3.00");
            assertThat(cart.list().get(0).getNumber()).isEqualTo(2);
        });
    }

    private void runDuringSubmission(Runnable competingAction) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        runDuringSubmission(() -> {
            started.countDown();
            competingAction.run();
        }, started);
    }

    private void runDuringSubmission(Runnable competingAction, CountDownLatch competitorStarted) throws Exception {
        CountDownLatch beforeCommit = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            beforeCommit.countDown();
            assertThat(releaseCommit.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(events).publishOrderTimeout(anyString());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> asUser(() -> orders.submit(request())));
            assertThat(beforeCommit.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> asUser(competingAction));
            assertThat(competitorStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseCommit.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            releaseCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void asUser(Runnable action) {
        BaseContext.setCurrentId(USER_ID);
        try {
            action.run();
        } finally {
            BaseContext.clear();
        }
    }

    private OrdersSubmitDTO request() {
        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAmount(new BigDecimal("2.50"));
        request.setPayMethod(1);
        return request;
    }

    private void assertSubmittedOnce() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, USER_ID))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, PRODUCT_ID))
                .isEqualTo(9);
    }
}
