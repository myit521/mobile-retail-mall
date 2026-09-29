package com.sky.order;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.inventory.api.InventoryService;
import com.sky.order.internal.application.OrderServiceImpl;
import com.sky.order.internal.messaging.OrderEventPublisher;
import com.sky.order.internal.persistence.OrderDetailMapper;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.observability.BusinessMetrics;
import com.sky.utils.WeChatPayUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTransitionTest {
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final OrderDetailMapper details = mock(OrderDetailMapper.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final WeChatPayUtil payment = mock(WeChatPayUtil.class);
    private final OrderEventPublisher events = mock(OrderEventPublisher.class);
    private final OrderServiceImpl service = new OrderServiceImpl();
    private final BusinessMetrics metrics = mock(BusinessMetrics.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "orderMapper", mapper);
        ReflectionTestUtils.setField(service, "orderDetailMapper", details);
        ReflectionTestUtils.setField(service, "inventoryService", inventory);
        ReflectionTestUtils.setField(service, "weChatPayUtil", payment);
        ReflectionTestUtils.setField(service, "orderEventPublisher", events);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        BaseContext.setCurrentId(10L);
    }

    @AfterEach
    void clearUser() {
        BaseContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin", "reject", "timeout"})
    void onlyCancellationWinnerChangesMetadataAndReturnsStock(String operation) throws Exception {
        Orders order = order(operation.equals("timeout") ? 1 : 2);
        stubRead(order);
        when(mapper.transition(eq(1L), nullable(Long.class), eq(order.getStatus()), eq(6), any()))
                .thenReturn(1);
        when(mapper.updateTransitionDetails(any())).thenReturn(1);

        cancel(operation);

        InOrder sequence = inOrder(mapper, payment, inventory);
        sequence.verify(mapper).transition(eq(1L), eq(operation.equals("user") ? 10L : null),
                eq(order.getStatus()), eq(6), any(LocalDateTime.class));
        if (!operation.equals("timeout")) {
            sequence.verify(payment).refund(eq("order-1"), anyString(), eq(new BigDecimal("12.50")), eq(new BigDecimal("12.50")));
        }
        sequence.verify(mapper).updateTransitionDetails(argThat(update -> update.getStatus() == null
                && update.getId().equals(1L)
                && (operation.equals("timeout") || update.getPayStatus().equals(2))));
        sequence.verify(inventory).returnStock(eq(1L), anyList());
        verifyNoInteractions(metrics);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin", "reject", "timeout"})
    void cancellationLoserAtSameTargetHasNoSideEffects(String operation) {
        stubRead(order(operation.equals("timeout") ? 1 : 2));
        when(mapper.selectForUpdate(eq(1L), nullable(Long.class))).thenReturn(order(6));

        cancel(operation);

        verify(mapper, never()).update(any());
        verify(mapper, never()).updateTransitionDetails(any());
        verifyNoInteractions(payment, inventory, details);
        BusinessMetrics.OrderOperation expected = operation.equals("reject")
                ? BusinessMetrics.OrderOperation.REJECT
                : operation.equals("timeout") ? BusinessMetrics.OrderOperation.TIMEOUT
                : BusinessMetrics.OrderOperation.CANCEL;
        verify(metrics).orderConflict(expected);
    }

    @Test
    void userCannotCancelForeignOrderOrUseMissingIdentityAsAdmin() {
        assertThatThrownBy(() -> service.userCancel(1L)).isInstanceOf(OrderBusinessException.class);
        BaseContext.clear();
        assertThatThrownBy(() -> service.userCancel(1L)).isInstanceOf(OrderBusinessException.class);
        verify(mapper, never()).transition(anyLong(), nullable(Long.class), anyInt(), anyInt(), any());
        verifyNoInteractions(payment, inventory);
    }

    @Test
    void foreignOwnedTargetIsNotAnIdempotentSuccess() {
        stubRead(order(2));
        Orders foreign = order(6);
        foreign.setUserId(11L);
        when(mapper.selectForUpdate(1L, 10L)).thenReturn(foreign);
        assertThatThrownBy(() -> service.userCancel(1L)).isInstanceOf(OrderBusinessException.class);
        verifyNoInteractions(payment, inventory);
    }

    @Test
    void changedStatusIsExplicitConflictWithoutRefund() {
        stubRead(order(2));
        when(mapper.selectForUpdate(1L, 10L)).thenReturn(order(3));
        assertThatThrownBy(() -> service.userCancel(1L))
                .isInstanceOf(OrderBusinessException.class).hasMessage(MessageConstant.ORDER_STATUS_ERROR);
        verifyNoInteractions(payment, inventory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin", "reject"})
    void completedOrdersCannotBeCancelled(String operation) {
        stubRead(order(5));
        assertThatThrownBy(() -> cancel(operation)).isInstanceOf(OrderBusinessException.class);
        verify(mapper, never()).transition(anyLong(), nullable(Long.class), anyInt(), anyInt(), any());
        verifyNoInteractions(payment, inventory);
    }

    @Test
    void rejectionRequiresPendingProcess() {
        stubRead(order(3));
        assertThatThrownBy(() -> cancel("reject")).isInstanceOf(OrderBusinessException.class);
        verifyNoInteractions(payment, inventory);
    }

    @Test
    void unpaidAdminCancellationWithoutOptionalReasonOnlyWritesTimedTransition() {
        stubRead(order(1));
        when(mapper.transition(eq(1L), isNull(), eq(1), eq(6), any())).thenReturn(1);
        when(mapper.updateTransitionDetails(any())).thenReturn(1);
        OrdersCancelDTO request = new OrdersCancelDTO();
        request.setId(1L);
        assertThat(service.adminCancel(request)).isEqualTo(MessageConstant.ORDER_CANCELLED);
        verify(mapper).transition(eq(1L), isNull(), eq(1), eq(6), any(LocalDateTime.class));
        verify(mapper, never()).updateTransitionDetails(any());
        verify(inventory).returnStock(eq(1L), anyList());
        verifyNoInteractions(payment);
    }

    @Test
    void confirmUsesConditionalStatusAndNeverGenericUpdate() {
        stubRead(order(2));
        when(mapper.transition(eq(1L), isNull(), eq(2), eq(3), any())).thenReturn(1);
        assertThat(service.confirm(confirmRequest())).isEqualTo(MessageConstant.ORDER_CONFIRM_SUCCESS);
        verify(mapper).transition(eq(1L), isNull(), eq(2), eq(3), any());
        verify(mapper, never()).update(any());
    }

    @Test
    void confirmLoserAcceptsOnlyConfirmedTarget() {
        stubRead(order(2));
        when(mapper.selectForUpdate(1L, null)).thenReturn(order(3), order(6));
        assertThat(service.confirm(confirmRequest())).isEqualTo(MessageConstant.ORDER_CONFIRM_SUCCESS);
        verify(metrics).orderConflict(BusinessMetrics.OrderOperation.ACCEPT);
        assertThatThrownBy(() -> service.confirm(confirmRequest())).isInstanceOf(OrderBusinessException.class);
        verify(metrics, times(2)).orderConflict(BusinessMetrics.OrderOperation.ACCEPT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothPaymentEntriesOnlyPublishForTransitionWinner(boolean legacy) {
        stubRead(order(1));
        when(mapper.transition(eq(1L), isNull(), eq(1), eq(2), any())).thenReturn(1);
        when(mapper.updateTransitionDetails(any())).thenReturn(1);
        pay(legacy);
        InOrder sequence = inOrder(mapper, events);
        sequence.verify(mapper).transition(eq(1L), isNull(), eq(1), eq(2), any());
        sequence.verify(mapper).updateTransitionDetails(argThat(update -> update.getStatus() == null && update.getPayStatus().equals(1)));
        sequence.verify(events).publishOrderPaid(argThat(event -> event.getOrderId().equals(1L)));
        verifyNoInteractions(metrics);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void paymentLosingToCancellationCannotResurrectOrder(boolean legacy) {
        stubRead(order(1));
        when(mapper.selectForUpdate(1L, null)).thenReturn(order(6));
        assertThatThrownBy(() -> pay(legacy)).isInstanceOf(OrderBusinessException.class);
        verify(mapper, never()).update(any());
        verifyNoInteractions(events);
        verify(metrics).orderConflict(BusinessMetrics.OrderOperation.PAYMENT);
    }

    @Test
    void duplicatePaymentWinnerDoesNotRepublish() {
        stubRead(order(1));
        when(mapper.selectForUpdate(1L, null)).thenReturn(order(2));
        pay(false);
        verify(mapper, never()).update(any());
        verifyNoInteractions(events);
        verify(metrics).orderConflict(BusinessMetrics.OrderOperation.PAYMENT);
    }

    @Test
    void transitionTargetMappingCoversReadyForPickupAndCompletedWithoutFallback() {
        ReflectionTestUtils.invokeMethod(service, "requireOperationForTarget",
                Orders.READY_FOR_PICKUP, BusinessMetrics.OrderOperation.DELIVERY);
        ReflectionTestUtils.invokeMethod(service, "requireOperationForTarget",
                Orders.COMPLETED, BusinessMetrics.OrderOperation.COMPLETE);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "requireOperationForTarget",
                99, BusinessMetrics.OrderOperation.REJECT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "requireOperationForTarget",
                Orders.PROCESSING, BusinessMetrics.OrderOperation.REJECT)).isInstanceOf(IllegalArgumentException.class);
    }

    private void pay(boolean legacy) {
        if (legacy) {
            service.paySuccess("order-1");
        } else {
            service.paySuccessWithValidation("order-1", "transaction", 12.5);
        }
    }

    private OrdersConfirmDTO confirmRequest() {
        OrdersConfirmDTO request = new OrdersConfirmDTO();
        request.setId(1L);
        return request;
    }

    private void cancel(String operation) {
        switch (operation) {
            case "user":
                assertThat(service.userCancel(1L).getCode()).isEqualTo(1);
                break;
            case "admin":
                OrdersCancelDTO cancel = new OrdersCancelDTO();
                cancel.setId(1L);
                cancel.setCancelReason("cancel");
                assertThat(service.adminCancel(cancel)).isEqualTo(MessageConstant.ORDER_CANCELLED);
                break;
            case "reject":
                OrdersRejectionDTO reject = new OrdersRejectionDTO();
                reject.setId(1L);
                reject.setRejectionReason("reject");
                assertThat(service.rejection(reject)).isEqualTo(MessageConstant.ORDER_REJECTION_SUCCESS);
                break;
            default:
                service.cancelTimeoutOrder("order-1");
        }
    }

    private void stubRead(Orders order) {
        when(mapper.selectByIdAndUserId(1L, 10L)).thenReturn(order);
        when(mapper.selectbyId(1L)).thenReturn(order);
        when(mapper.getByNumber("order-1")).thenReturn(order);
    }

    private Orders order(int status) {
        return Orders.builder().id(1L).userId(10L).number("order-1").status(status)
                .payStatus(status == 1 ? 0 : 1).amount(new BigDecimal("12.50")).build();
    }
}
