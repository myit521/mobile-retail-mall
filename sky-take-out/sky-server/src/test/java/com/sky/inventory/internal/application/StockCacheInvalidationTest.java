package com.sky.inventory.internal.application;

import com.sky.constant.CacheConstant;
import com.sky.dto.StockAdjustDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Product;
import com.sky.entity.StockCheckRecord;
import com.sky.inventory.api.InventoryService;
import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockCheckRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockCacheInvalidationTest {
    private AnnotationConfigApplicationContext context;
    private InventoryService service;
    private InventoryProductMapper products;
    private StockCheckRecordMapper records;
    private TransactionTemplate transaction;
    private Cache cache;

    enum Mutation { DEDUCT, RETURN, ADJUST, CONFIRM }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.register(StockServiceImpl.class, StockServiceImplContextTest.MapperCollaborators.class,
                TransactionAndCacheConfig.class);
        context.refresh();
        service = context.getBean(InventoryService.class);
        products = context.getBean(InventoryProductMapper.class);
        records = context.getBean(StockCheckRecordMapper.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        cache = context.getBean(CacheManager.class).getCache(CacheConstant.PRODUCT_CACHE);
        List<Product> staleProducts = List.of(Product.builder().id(7L).stock(0).build());
        cache.put("category:1", staleProducts);
        cache.put("phoneModel:2", staleProducts);

        when(products.selectProductForUpdate(7L)).thenReturn(Product.builder().id(7L).stock(8).build());
        when(products.deductIfAvailable(7L, 2)).thenReturn(1);
        when(products.releaseOnce(9L, "ORDER_CLOSED")).thenReturn(1);
        when(products.returnStock(7L, 2)).thenReturn(1);
        when(products.adjustIfAvailable(7L, 2)).thenReturn(1);
        when(records.selectById(9L)).thenReturn(StockCheckRecord.builder()
                .id(9L).productId(7L).actualStock(10).diffQuantity(2)
                .status(StockCheckRecord.STATUS_PENDING).build());
        when(records.confirmPending(9L)).thenReturn(1);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void clearsBothProductListsOnlyAfterOuterTransactionCommits(Mutation mutation) {
        transaction.executeWithoutResult(status -> {
            mutate(mutation);
            assertBothListsCached();
        });

        assertNull(cache.get("category:1"));
        assertNull(cache.get("phoneModel:2"));
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void keepsBothProductListsWhenOuterTransactionRollsBack(Mutation mutation) {
        transaction.executeWithoutResult(status -> {
            mutate(mutation);
            assertBothListsCached();
            status.setRollbackOnly();
        });

        assertBothListsCached();
    }

    @Test
    void duplicateReturnKeepsProductListsCached() {
        when(products.releaseOnce(9L, "ORDER_CLOSED")).thenReturn(0);

        service.returnStock(9L, details());

        assertBothListsCached();
    }

    @Test
    void returnWithoutUpdatedProductsKeepsProductListsCached() {
        when(products.returnStock(7L, 2)).thenReturn(0);

        service.returnStock(9L, details());

        assertBothListsCached();
    }

    @Test
    void zeroAdjustmentKeepsProductListsCached() {
        service.adjustStock(adjustment(0));

        assertBothListsCached();
    }

    @Test
    void committedAdjustmentStillSucceedsWhenCacheInvalidationFails() {
        doThrow(new IllegalStateException("cache unavailable"))
                .when(context.getBean(ConcurrentMapCache.class)).invalidate();

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status ->
                service.adjustStock(adjustment(2))));

        verify(products).adjustIfAvailable(7L, 2);
    }

    @Test
    void confirmationWithoutStockDifferenceKeepsProductListsCached() {
        when(records.selectById(9L)).thenReturn(StockCheckRecord.builder()
                .id(9L).productId(7L).actualStock(8).diffQuantity(0)
                .status(StockCheckRecord.STATUS_PENDING).build());

        service.confirmCheckRecord(9L);

        assertBothListsCached();
    }

    private void mutate(Mutation mutation) {
        switch (mutation) {
            case DEDUCT:
                service.deductStock(9L, details());
                break;
            case RETURN:
                service.returnStock(9L, details());
                break;
            case ADJUST:
                service.adjustStock(adjustment(2));
                break;
            case CONFIRM:
                service.confirmCheckRecord(9L);
                break;
            default:
                throw new IllegalArgumentException("Unknown mutation: " + mutation);
        }
    }

    private List<OrderDetail> details() {
        return List.of(OrderDetail.builder().productId(7L).number(2).build());
    }

    private StockAdjustDTO adjustment(int quantity) {
        StockAdjustDTO dto = new StockAdjustDTO();
        dto.setProductId(7L);
        dto.setQuantity(quantity);
        return dto;
    }

    private void assertBothListsCached() {
        assertNotNull(cache.get("category:1"));
        assertNotNull(cache.get("phoneModel:2"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionAndCacheConfig {
        @Bean
        ConcurrentMapCache productCache() {
            return spy(new ConcurrentMapCache(CacheConstant.PRODUCT_CACHE));
        }

        @Bean
        CacheManager cacheManager(ConcurrentMapCache productCache) {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(productCache));
            manager.afterPropertiesSet();
            return new TransactionAwareCacheManagerProxy(manager);
        }

        @Bean
        PlatformTransactionManager transactionManager() throws SQLException {
            DataSource source = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            return new DataSourceTransactionManager(source);
        }
    }
}
