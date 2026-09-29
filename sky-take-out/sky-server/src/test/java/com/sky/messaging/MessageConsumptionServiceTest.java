package com.sky.messaging;

import com.sky.messaging.consumer.MessageConsumptionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class MessageConsumptionServiceTest {

    @Test
    void duplicateUniqueKeyAcknowledgesWithoutRepeatingSideEffect() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1)
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenAnswer(invocation -> row(invocation, "COMPLETED", "order-1"));
        MessageConsumptionService service = new MessageConsumptionService(jdbc);
        AtomicInteger effects = new AtomicInteger();

        assertThat(service.consume("consumer", "event-1", "order-1", effects::incrementAndGet)).isTrue();
        assertThat(service.consume("consumer", "event-1", "order-1", effects::incrementAndGet)).isFalse();

        assertThat(effects).hasValue(1);
    }

    @Test
    void failedOrInFlightIdentityIsNotAcknowledgedAsCompletedDuplicate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenAnswer(invocation -> row(invocation, "FAILED", "order-1"));
        MessageConsumptionService service = new MessageConsumptionService(jdbc);
        Runnable action = mock(Runnable.class);

        assertThatThrownBy(() -> service.consume("consumer", "event-1", "order-1", action))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not completed");
        verify(action, never()).run();
    }

    @Test
    void completedIdentityCannotBeReboundToAnotherBusinessKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenAnswer(invocation -> row(invocation, "COMPLETED", "order-original"));
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        assertThatThrownBy(() -> service.consume("consumer", "event-1", "order-rebound", () -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business key");
    }

    @Test
    void overlengthIdentityIsRejectedBeforeDatabaseClaim() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        assertThatThrownBy(() -> service.consume("consumer", "e".repeat(65), "order-1", () -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void overlengthBusinessKeyIsRejectedBeforeDatabaseClaim() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        assertThatThrownBy(() -> service.consume("consumer", "event-1", "b".repeat(129), () -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessKey");
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void nonDuplicateInsertFailureIsNotConvertedIntoIdempotentAcknowledgement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("invalid-value"));
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        assertThatThrownBy(() -> service.consume("consumer", "event-1", "order-1", () -> { }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("invalid-value");
    }

    @Test
    void processingFailureEscapesSoTransactionCanRollBackAndBrokerCanRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        assertThatThrownBy(() -> service.consume("consumer", "event-2", "order-2",
                () -> { throw new IllegalStateException("poison"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("poison");
    }

    @Test
    void finalFailureIsBoundedAndSanitizedBeforePersistence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        service.recordFailure("consumer", "event-3", "order-3", "token=very-secret\nfailed");

        verify(jdbc).update(anyString(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("token=[redacted] failed"));
    }

    @Test
    void terminalFailureCannotRebindExistingIdentityToAnotherBusinessKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
                .thenAnswer(invocation -> row(invocation, "FAILED", "order-original"));
        MessageConsumptionService service = new MessageConsumptionService(jdbc);

        assertThatThrownBy(() -> service.recordFailure("consumer", "event-3", "order-rebound", "failed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business key");
    }

    @SuppressWarnings("unchecked")
    private Object row(org.mockito.invocation.InvocationOnMock invocation, String status, String businessKey) throws Exception {
        java.sql.ResultSet resultSet = mock(java.sql.ResultSet.class);
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getString("business_key")).thenReturn(businessKey);
        org.springframework.jdbc.core.RowMapper<Object> mapper = invocation.getArgument(1);
        return mapper.mapRow(resultSet, 0);
    }
}
