package com.sky.order;

import com.sky.exception.OrderBusinessException;
import com.sky.order.internal.OrderTransition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTransitionTest {

    @ParameterizedTest
    @CsvSource({"1,2", "2,3", "3,4", "4,5", "1,6", "2,6", "3,6", "4,6"})
    void allowsPaymentConfirmPickupCompletionAndActiveCancellation(int from, int to) {
        assertThatCode(() -> OrderTransition.requireAllowed(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1,3", "1,4", "1,5", "2,1", "2,2", "2,4", "2,5",
            "3,1", "3,2", "3,3", "3,5", "4,1", "4,2", "4,3", "4,4",
            "5,1", "5,2", "5,3", "5,4", "5,5", "5,6",
            "6,1", "6,2", "6,3", "6,4", "6,5", "6,6", "0,2", "7,6", "1,0", "1,7"})
    void rejectsSkippedBackwardTerminalAndUnknownTransitions(int from, int to) {
        assertThatThrownBy(() -> OrderTransition.requireAllowed(from, to))
                .isInstanceOf(OrderBusinessException.class);
    }
}
