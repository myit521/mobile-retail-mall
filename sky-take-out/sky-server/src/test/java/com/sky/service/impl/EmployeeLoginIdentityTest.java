package com.sky.service.impl;

import com.sky.auth.internal.application.EmployeeServiceImpl;
import com.sky.auth.internal.application.LoginAttemptService;
import com.sky.auth.internal.persistence.EmployeeMapper;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.exception.PasswordErrorException;
import com.sky.exception.TooManyAttemptsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLoginIdentityTest {
    @InjectMocks private EmployeeServiceImpl service;
    @Mock private EmployeeMapper mapper;
    @Mock private LoginAttemptService attempts;
    @Mock private PasswordEncoder encoder;

    @Test
    void trailingSpacesCannotBypassCanonicalAccountLock() {
        doAnswer(invocation -> {
            if ("admin".equals(invocation.getArgument(0))) throw new TooManyAttemptsException("locked");
            return null;
        }).when(attempts).checkLocked(anyString());
        assertThrows(TooManyAttemptsException.class, () -> service.login(login("admin   ")));
        verifyNoInteractions(mapper, encoder);
    }

    @Test
    void trailingSpacesUseSameLookupAndFailureCounter() {
        when(mapper.getByUsername(anyString())).thenReturn(employee("admin"));
        assertThrows(PasswordErrorException.class, () -> service.login(login("admin ")));
        verify(attempts).recordFailure("admin");
    }

    @Test
    void successfulPaddedLoginClearsCanonicalCounter() {
        Employee employee = employee("admin");
        when(mapper.getByUsername(anyString())).thenReturn(employee);
        when(encoder.matches("pwd", "hash")).thenReturn(true);
        assertSame(employee, service.login(login("admin  ")));
        verify(attempts).clearFailure("admin");
        assertEquals("ADMIN", employee.getRole());
    }

    @Test
    void leadingSpacesAndCaseRemainPartOfUsername() {
        Employee employee = employee(" Admin");
        when(mapper.getByUsername(" Admin")).thenReturn(employee);
        when(encoder.matches("pwd", "hash")).thenReturn(true);
        assertSame(employee, service.login(login(" Admin")));
        verify(attempts).checkLocked(" Admin");
    }

    private EmployeeLoginDTO login(String username) {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername(username);
        dto.setPassword("pwd");
        return dto;
    }

    private Employee employee(String username) {
        return Employee.builder().id(1L).username(username).password("hash").status(1).role("ADMIN").build();
    }
}
