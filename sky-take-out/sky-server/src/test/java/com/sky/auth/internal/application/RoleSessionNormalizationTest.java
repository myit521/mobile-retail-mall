package com.sky.auth.internal.application;

import com.sky.auth.internal.persistence.EmployeeMapper;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleSessionNormalizationTest {
    @ParameterizedTest
    @CsvSource(value = {"admin,ADMIN", "' ADMIN ',ADMIN", "' employee ',EMPLOYEE", "invalid,EMPLOYEE", "NULL,EMPLOYEE", "'',EMPLOYEE"}, nullValues = "NULL")
    void loginAndSessionUseSameRoleWithoutAcceptingRoleChanges(String rawRole, String expectedRole) {
        EmployeeMapper employees = mock(EmployeeMapper.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        EmployeeServiceImpl login = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(login, "employeeMapper", employees);
        ReflectionTestUtils.setField(login, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(login, "loginAttemptService", mock(LoginAttemptService.class));
        when(employees.getByUsername("legacy")).thenReturn(Employee.builder()
                .id(7L).username("legacy").password("hash").status(1).role(rawRole).build());
        when(passwords.matches("password", "hash")).thenReturn(true);
        EmployeeLoginDTO credentials = new EmployeeLoginDTO();
        credentials.setUsername("legacy");
        credentials.setPassword("password");
        Employee authenticated = login.login(credentials);
        assertEquals(expectedRole, authenticated.getRole());

        TokenSessionServiceImpl sessions = new TokenSessionServiceImpl();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations hash = mock(HashOperations.class);
        ReflectionTestUtils.setField(sessions, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(sessions, "employeeMapper", employees);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.get("login:admin:session", "id")).thenReturn("7");
        when(hash.get("login:admin:session", "role")).thenReturn(authenticated.getRole());
        // A fresh mapper result preserves the original database value after login.
        Employee persisted = Employee.builder().id(7L).status(1).role(rawRole).build();
        when(employees.getEmployeeById(7L)).thenReturn(persisted);
        assertTrue(sessions.isAdminSessionValid("session", 7L, expectedRole));
        assertTrue(sessions.isAdminSessionValid("session", 7L));
        assertTrue(sessions.isAdminSessionValid("session"));
        assertFalse(sessions.isAdminSessionValid("session", 8L, expectedRole));
        assertFalse(sessions.isAdminSessionValid("session", 7L, "SUPERADMIN"));
        persisted.setStatus(0);
        assertFalse(sessions.isAdminSessionValid("session", 7L, expectedRole));
        persisted.setStatus(1);
        persisted.setRole("ADMIN".equals(expectedRole) ? "EMPLOYEE" : "ADMIN");
        assertFalse(sessions.isAdminSessionValid("session", 7L, expectedRole));
        assertFalse(sessions.isAdminSessionValid("session", 7L));
        assertFalse(sessions.isAdminSessionValid("session"));
    }
}
