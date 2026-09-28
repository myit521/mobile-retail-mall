package com.sky.inventory.internal.application;

import com.sky.entity.OrderDetail;
import com.sky.entity.Product;
import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockServiceImplTest {

    @Test
    void productDeletedBetweenLookupAndIncrementContributesNoAggregateOrReleaseLog() {
        long orderId = 98001L;
        long productId = 98002L;
        InventoryProductMapper productMapper = mock(InventoryProductMapper.class);
        StockLogMapper stockLogMapper = mock(StockLogMapper.class);
        when(productMapper.releaseOnce(orderId, "ORDER_CLOSED")).thenReturn(1);
        when(productMapper.selectProductById(productId)).thenReturn(Product.builder()
                .id(productId)
                .name("deleted-before-increment")
                .stock(5)
                .status(0)
                .alertThreshold(1)
                .build());
        when(productMapper.returnStock(productId, 2)).thenReturn(0);
        StockServiceImpl service = new StockServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "stockLogMapper", stockLogMapper);

        service.returnStock(orderId, List.of(OrderDetail.builder()
                .orderId(orderId)
                .productId(productId)
                .number(2)
                .build()));

        verify(productMapper).completeRelease(orderId, "ORDER_CLOSED", 0);
        verifyNoInteractions(stockLogMapper);
    }
}
