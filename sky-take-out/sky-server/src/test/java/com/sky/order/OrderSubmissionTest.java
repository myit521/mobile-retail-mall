package com.sky.order;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.Product;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.inventory.api.InventoryService;
import com.sky.order.internal.application.OrderServiceImpl;
import com.sky.order.internal.messaging.OrderEventPublisher;
import com.sky.order.internal.persistence.OrderDetailMapper;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.order.internal.support.OrderNumberGenerator;
import com.sky.product.api.ShoppingCartService;
import com.sky.product.internal.persistence.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OrderSubmissionTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderDetailMapper details = mock(OrderDetailMapper.class);
    private final ShoppingCartService cart = mock(ShoppingCartService.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final OrderEventPublisher events = mock(OrderEventPublisher.class);
    private final OrderNumberGenerator numbers = mock(OrderNumberGenerator.class);

    @AfterEach
    void clearUser() {
        BaseContext.clear();
    }

    @Test
    void usesCurrentProductPriceForOrderAndDetails() {
        BaseContext.setCurrentId(42L);
        ShoppingCart item = ShoppingCart.builder().productId(7L).number(2)
                .amount(new BigDecimal("1.00")).build();
        when(cart.list()).thenReturn(List.of(item));
        when(products.selectByIds(anyList())).thenReturn(List.of(Product.builder().id(7L)
                .status(1).price(new BigDecimal("12.50")).build()));
        when(numbers.nextOrderNumber()).thenReturn("order-1");
        doAnswer(invocation -> {
            invocation.<Orders>getArgument(0).setId(99L);
            return 1;
        }).when(orders).insert(any(Orders.class));

        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAmount(new BigDecimal("25.00"));
        request.setPayMethod(1);
        OrderServiceImpl service = service();

        assertThat(service.submit(request).getOrderAmount()).isEqualByComparingTo("25.00");
        var order = org.mockito.ArgumentCaptor.forClass(Orders.class);
        verify(orders).insert(order.capture());
        assertThat(order.getValue().getAmount()).isEqualByComparingTo("25.00");
        @SuppressWarnings("unchecked")
        var orderDetails = org.mockito.ArgumentCaptor.forClass((Class<List<OrderDetail>>) (Class<?>) List.class);
        verify(details).insertBatch(orderDetails.capture());
        assertThat(orderDetails.getValue().get(0).getAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void rejectsClientAmountThatDiffersFromCurrentProductPrices() {
        BaseContext.setCurrentId(42L);
        when(cart.list()).thenReturn(List.of(ShoppingCart.builder().productId(7L)
                .number(2).amount(new BigDecimal("1.00")).build()));
        when(products.selectByIds(anyList())).thenReturn(List.of(Product.builder().id(7L)
                .status(1).price(new BigDecimal("12.50")).build()));
        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAmount(new BigDecimal("0.01"));

        assertThatThrownBy(() -> service().submit(request))
                .isInstanceOf(OrderBusinessException.class);
        verify(orders, never()).insert(any(Orders.class));
    }

    @Test
    void locksUserBeforeReadingCartSoConcurrentSubmissionsCannotReuseItems() {
        BaseContext.setCurrentId(42L);
        when(cart.list()).thenReturn(List.of());
        OrdersSubmitDTO request = new OrdersSubmitDTO();

        assertThatThrownBy(() -> service().submit(request))
                .isInstanceOf(OrderBusinessException.class);

        var order = inOrder(orders, cart);
        order.verify(orders).lockUserForSubmit(42L);
        order.verify(cart).list();
        verify(orders, never()).insert(any(Orders.class));
    }

    private OrderServiceImpl service() {
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        ReflectionTestUtils.setField(service, "orderDetailMapper", details);
        ReflectionTestUtils.setField(service, "shoppingCartService", cart);
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "inventoryService", inventory);
        ReflectionTestUtils.setField(service, "orderEventPublisher", events);
        ReflectionTestUtils.setField(service, "orderNumberGenerator", numbers);
        return service;
    }
}
