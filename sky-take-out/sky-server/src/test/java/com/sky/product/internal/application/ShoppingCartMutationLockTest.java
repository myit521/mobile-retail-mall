package com.sky.product.internal.application;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.ShoppingCart;
import com.sky.inventory.api.InventoryService;
import com.sky.product.internal.persistence.ProductMapper;
import com.sky.product.internal.persistence.ShoppingCartMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ShoppingCartMutationLockTest {
    private static final long USER_ID = 17L;

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void addAndSubLockUserBeforeReadingCart() throws Exception {
        ShoppingCartMapper mapper = mock(ShoppingCartMapper.class);
        ShoppingCartServiceImpl service = service(mapper);
        BaseContext.setCurrentId(USER_ID);
        ShoppingCartDTO dto = new ShoppingCartDTO();

        service.add(dto);
        service.sub(dto);

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockUserForMutation(USER_ID);
        order.verify(mapper).selectlist(any(ShoppingCart.class));
        order.verify(mapper).insert(any(ShoppingCart.class));
        order.verify(mapper).lockUserForMutation(USER_ID);
        order.verify(mapper).selectlist(any(ShoppingCart.class));
        assertNotNull(ShoppingCartServiceImpl.class.getMethod("add", ShoppingCartDTO.class).getAnnotation(Transactional.class));
        assertNotNull(ShoppingCartServiceImpl.class.getMethod("sub", ShoppingCartDTO.class).getAnnotation(Transactional.class));
    }

    @Test
    void cleanAndRestoreLockUserBeforeMutation() throws Exception {
        ShoppingCartMapper mapper = mock(ShoppingCartMapper.class);
        ShoppingCartServiceImpl service = service(mapper);
        BaseContext.setCurrentId(USER_ID);

        service.clean();
        service.restore(List.of(OrderDetail.builder().productId(4L).number(1).build()));

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockUserForMutation(USER_ID);
        order.verify(mapper).deleteByUserId(USER_ID);
        order.verify(mapper).lockUserForMutation(USER_ID);
        order.verify(mapper).selectlist(any(ShoppingCart.class));
        order.verify(mapper).insert(any(ShoppingCart.class));
        assertNotNull(ShoppingCartServiceImpl.class.getMethod("clean").getAnnotation(Transactional.class));
        assertNotNull(ShoppingCartServiceImpl.class.getMethod("restore", List.class).getAnnotation(Transactional.class));
    }

    private ShoppingCartServiceImpl service(ShoppingCartMapper mapper) {
        ShoppingCartServiceImpl service = new ShoppingCartServiceImpl();
        ReflectionTestUtils.setField(service, "shoppingCartMapper", mapper);
        ReflectionTestUtils.setField(service, "productMapper", mock(ProductMapper.class));
        ReflectionTestUtils.setField(service, "stockService", mock(InventoryService.class));
        return service;
    }
}
