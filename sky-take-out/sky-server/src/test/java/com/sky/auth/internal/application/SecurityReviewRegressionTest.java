package com.sky.auth.internal.application;

import com.sky.auth.internal.persistence.EmployeeMapper;
import com.sky.dto.EmployeeDTO;
import com.sky.entity.Employee;
import com.sky.mapper.ReportMapper;
import com.sky.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityReviewRegressionTest {
    @Test
    void updatingEmployeeWithoutRolePreservesRole() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        EmployeeServiceImpl service = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(service, "employeeMapper", mapper);
        EmployeeDTO request = new EmployeeDTO();
        request.setId(7L);
        request.setPhone("12345678901");
        when(mapper.updateEmployee(any())).thenAnswer(call -> {
            Employee saved = call.getArgument(0);
            assertNull(saved.getRole());
            return 1;
        });
        assertTrue(service.update(request));
    }

    @Test
    void turnoverRejectsHugeRangeBeforeQuery() {
        ReportServiceImpl service = new ReportServiceImpl();
        ReflectionTestUtils.setField(service, "reportMapper", mock(ReportMapper.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.getTurnoverStatistics(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void ordersRejectReverseRangeBeforeQuery() {
        ReportServiceImpl service = new ReportServiceImpl();
        ReflectionTestUtils.setField(service, "reportMapper", mock(ReportMapper.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.getOrderStatistics(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9)));
    }

    @Test
    void adminSessionRejectsForgedRoleAndChangedDatabaseRole() {
        TokenSessionServiceImpl sessions = new TokenSessionServiceImpl();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations hash = mock(HashOperations.class);
        EmployeeMapper employees = mock(EmployeeMapper.class);
        ReflectionTestUtils.setField(sessions, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(sessions, "employeeMapper", employees);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.get("login:admin:session", "id")).thenReturn("7");
        when(hash.get("login:admin:session", "role")).thenReturn("EMPLOYEE");
        assertFalse(sessions.isAdminSessionValid("session", 7L, "ADMIN"));
        when(hash.get("login:admin:session", "role")).thenReturn("ADMIN");
        when(employees.getEmployeeById(7L)).thenReturn(Employee.builder().id(7L).role("EMPLOYEE").status(1).build());
        assertFalse(sessions.isAdminSessionValid("session", 7L, "ADMIN"));
        assertFalse(sessions.isAdminSessionValid("session", 7L));
        assertFalse(sessions.isAdminSessionValid("session"));
        when(employees.getEmployeeById(7L)).thenReturn(Employee.builder().id(7L).role("ADMIN").status(1).build());
        assertTrue(sessions.isAdminSessionValid("session", 7L));
        assertTrue(sessions.isAdminSessionValid("session"));
    }
}
