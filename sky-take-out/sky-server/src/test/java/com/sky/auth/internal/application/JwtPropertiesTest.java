package com.sky.auth.internal.application;

import com.sky.properties.JwtProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtPropertiesTest {
    @Test
    void missingSecretsFailValidation() {
        JwtProperties properties = new JwtProperties();
        assertThrows(IllegalStateException.class, properties::validateSecretKeys);
    }
}
