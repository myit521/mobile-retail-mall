package com.sky.service.impl;

import com.sky.auth.internal.application.UserServiceImpl;
import com.sky.auth.internal.persistence.UserMapper;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.WeChatProperties;
import com.sky.utils.HttpClientUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLoginConcurrencyTest {
    @InjectMocks private UserServiceImpl service;
    @Mock private UserMapper mapper;
    @Mock private WeChatProperties properties;

    @Test
    void losingInsertRaceReturnsTheAlreadyCreatedUser() {
        User winner = User.builder().id(42L).openid("openid").build();
        when(mapper.selectByOpenid("openid")).thenReturn(null, winner);
        doThrow(new DuplicateKeyException("openid already created")).when(mapper).insert(any(User.class));
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.doGet(anyString(), anyMap())).thenReturn("{\"openid\":\"openid\"}");
            assertSame(winner, service.wxlogin(login()));
        }
    }

    @Test
    void unrelatedDuplicateKeyFailureIsNotHiddenWhenUserDoesNotExist() {
        DuplicateKeyException failure = new DuplicateKeyException("other constraint");
        doThrow(failure).when(mapper).insert(any(User.class));
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.doGet(anyString(), anyMap())).thenReturn("{\"openid\":\"openid\"}");
            assertSame(failure, assertThrows(DuplicateKeyException.class, () -> service.wxlogin(login())));
        }
    }

    private UserLoginDTO login() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setCode("test-code");
        return dto;
    }
}
