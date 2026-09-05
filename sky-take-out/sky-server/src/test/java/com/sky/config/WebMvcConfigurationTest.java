package com.sky.config;

import com.sky.order.internal.controller.user.OrderController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import springfox.documentation.RequestHandler;
import springfox.documentation.spi.service.contexts.ApiSelector;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.wrapper.PatternsRequestCondition;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;

class WebMvcConfigurationTest {

    @Test
    void swaggerIncludesModuleOwnedControllers() {
        Docket docket = new WebMvcConfiguration().docket();
        ApiSelector selector = (ApiSelector) ReflectionTestUtils.getField(docket, "apiSelector");
        RequestHandler handler = mock(RequestHandler.class);
        PatternsRequestCondition<?> patterns = mock(PatternsRequestCondition.class);
        doReturn(OrderController.class).when(handler).declaringClass();
        doReturn(patterns).when(handler).getPatternsCondition();
        doReturn(Collections.singleton("/user/order")).when(patterns).getPatterns();

        assertTrue(selector.getRequestHandlerSelector().test(handler));
    }
}
