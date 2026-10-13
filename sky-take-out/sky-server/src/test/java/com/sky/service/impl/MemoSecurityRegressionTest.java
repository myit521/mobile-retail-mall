package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.Memo;
import com.sky.exception.BaseException;
import com.sky.mapper.MemoMapper;
import com.sky.notification.api.OrderNotificationPort;
import com.sky.task.MemoTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoSecurityRegressionTest {
    @AfterEach
    void clear() { BaseContext.clear(); }

    @Test
    void sameNumericIdCannotReadOtherOwnerType() {
        BaseContext.setCurrentId(7L);
        BaseContext.setCurrentRole("USER");
        BaseContext.setCurrentOwnerType("USER");
        MemoMapper mapper = mock(MemoMapper.class);
        Memo memo = Memo.builder().id(3L).userId(7L).ownerType("ADMIN").content("admin private").build();
        when(mapper.getByIdAndUserId(3L, 7L, "USER")).thenReturn(memo);
        MemoServiceImpl service = new MemoServiceImpl();
        ReflectionTestUtils.setField(service, "memoMapper", mapper);
        assertThrows(BaseException.class, () -> service.getById(3L));
    }

    @Test
    void failedReminderMustRemainPending() {
        MemoMapper mapper = mock(MemoMapper.class);
        Memo memo = Memo.builder().id(3L).userId(7L).ownerType("USER").title("Reminder").build();
        when(mapper.selectNeedRemind(any(LocalDateTime.class))).thenReturn(List.of(memo));
        when(mapper.insertUserReminder(3L)).thenThrow(new IllegalStateException("database unavailable"));
        MemoTask task = new MemoTask();
        ReflectionTestUtils.setField(task, "memoMapper", mapper);
        task.checkRemind();
        verify(mapper, never()).batchUpdateReminded(anyList());
    }

    @Test
    void overdueUserMemoCannotBeSentToEmployeeWithSameId() {
        MemoMapper mapper = mock(MemoMapper.class);
        OrderNotificationPort port = mock(OrderNotificationPort.class);
        Memo memo = Memo.builder().id(5L).userId(7L).ownerType("USER").build();
        when(mapper.selectOverdue(any(LocalDateTime.class))).thenReturn(List.of(memo));
        MemoTask task = new MemoTask();
        ReflectionTestUtils.setField(task, "memoMapper", mapper);
        ReflectionTestUtils.setField(task, "notificationPort", port);
        task.checkOverdue();
        verify(port, never()).sendTo(anyString(), anyString());
        verify(mapper).insertUserReminder(5L);
    }
}
