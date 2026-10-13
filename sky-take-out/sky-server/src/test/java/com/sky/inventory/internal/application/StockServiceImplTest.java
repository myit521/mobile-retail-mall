package com.sky.inventory.internal.application;

import com.sky.entity.OrderDetail;
import com.sky.entity.Product;
import com.sky.entity.StockCheckRecord;
import com.sky.dto.StockAdjustDTO;
import com.sky.dto.StockCheckRecordDTO;
import com.sky.exception.BaseException;
import com.sky.inventory.internal.persistence.InventoryProductMapper;
import com.sky.inventory.internal.persistence.StockCheckRecordMapper;
import com.sky.inventory.internal.persistence.StockLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockServiceImplTest {

    @Test
    void adjustUsesLockedCurrentStockAndAtomicDelta() {
        InventoryProductMapper products = mock(InventoryProductMapper.class);
        StockLogMapper logs = mock(StockLogMapper.class);
        when(products.selectProductForUpdate(7L)).thenReturn(Product.builder().id(7L).stock(8).build());
        when(products.adjustIfAvailable(7L, -3)).thenReturn(1);
        StockServiceImpl service = new StockServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "stockLogMapper", logs);
        StockAdjustDTO dto = new StockAdjustDTO();
        dto.setProductId(7L);
        dto.setQuantity(-3);

        service.adjustStock(dto);

        verify(products).selectProductForUpdate(7L);
        verify(products).adjustIfAvailable(7L, -3);
        verify(logs).insert(org.mockito.ArgumentMatchers.argThat(log ->
                log.getBeforeStock() == 8 && log.getAfterStock() == 5));
    }

    @Test
    void zeroAdjustmentKeepsExistingNoOpBehavior() {
        InventoryProductMapper products = mock(InventoryProductMapper.class);
        StockLogMapper logs = mock(StockLogMapper.class);
        when(products.selectProductForUpdate(7L)).thenReturn(Product.builder().id(7L).stock(8).build());
        StockServiceImpl service = new StockServiceImpl();
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "stockLogMapper", logs);
        StockAdjustDTO dto = new StockAdjustDTO();
        dto.setProductId(7L);
        dto.setQuantity(0);

        service.adjustStock(dto);

        verify(products, never()).adjustIfAvailable(7L, 0);
        verify(logs).insert(org.mockito.ArgumentMatchers.argThat(log ->
                log.getBeforeStock() == 8 && log.getAfterStock() == 8));
    }

    @Test
    void duplicateConfirmationDoesNotChangeStock() {
        StockCheckRecordMapper records = mock(StockCheckRecordMapper.class);
        InventoryProductMapper products = mock(InventoryProductMapper.class);
        when(records.selectById(9L)).thenReturn(StockCheckRecord.builder()
                .id(9L).productId(7L).actualStock(4).diffQuantity(-2)
                .status(StockCheckRecord.STATUS_PENDING).build());
        StockServiceImpl service = new StockServiceImpl();
        ReflectionTestUtils.setField(service, "stockCheckRecordMapper", records);
        ReflectionTestUtils.setField(service, "productMapper", products);

        assertThrows(BaseException.class, () -> service.confirmCheckRecord(9L));

        verify(records).confirmPending(9L);
        verifyNoInteractions(products);
    }

    @Test
    void confirmationAppliesRecordedDifferenceToLockedCurrentStock() {
        StockCheckRecordMapper records = mock(StockCheckRecordMapper.class);
        InventoryProductMapper products = mock(InventoryProductMapper.class);
        StockLogMapper logs = mock(StockLogMapper.class);
        when(records.selectById(9L)).thenReturn(StockCheckRecord.builder()
                .id(9L).productId(7L).actualStock(4).diffQuantity(-2)
                .status(StockCheckRecord.STATUS_PENDING).build());
        when(records.confirmPending(9L)).thenReturn(1);
        when(products.selectProductForUpdate(7L)).thenReturn(Product.builder().id(7L).stock(10).build());
        when(products.adjustIfAvailable(7L, -2)).thenReturn(1);
        StockServiceImpl service = new StockServiceImpl();
        ReflectionTestUtils.setField(service, "stockCheckRecordMapper", records);
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "stockLogMapper", logs);

        service.confirmCheckRecord(9L);

        verify(products).adjustIfAvailable(7L, -2);
        verify(logs).insert(org.mockito.ArgumentMatchers.argThat(log ->
                log.getBeforeStock() == 10 && log.getAfterStock() == 8 && log.getChangeQuantity() == -2));
    }

    @Test
    void negativePhysicalCountIsRejectedBeforePersistence() {
        StockServiceImpl service = new StockServiceImpl();
        StockCheckRecordDTO dto = new StockCheckRecordDTO();
        dto.setActualStock(-1);

        assertThrows(BaseException.class, () -> service.submitCheckRecord(dto));
    }

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
