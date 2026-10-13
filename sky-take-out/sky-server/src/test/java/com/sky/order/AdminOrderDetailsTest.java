package com.sky.order;

import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.order.internal.application.OrderServiceImpl;
import com.sky.order.internal.persistence.OrderDetailMapper;
import com.sky.order.internal.persistence.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminOrderDetailsTest {
    @AfterEach
    void clearUser() {
        BaseContext.clear();
    }

    @Test
    void adminCanReadAnOrderOwnedByAnotherUser() {
        BaseContext.setCurrentId(10L);
        OrderMapper mapper = mock(OrderMapper.class);
        OrderDetailMapper details = mock(OrderDetailMapper.class);
        when(mapper.selectbyId(99L)).thenReturn(Orders.builder().id(99L).userId(20L).build());
        when(details.selectByOrderId(99L)).thenReturn(List.of());
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", details);

        assertThat(service.showForAdmin(99L).getId()).isEqualTo(99L);
        verify(mapper).selectbyId(99L);
        verify(mapper, never()).selectByIdAndUserId(anyLong(), anyLong());
    }
}
