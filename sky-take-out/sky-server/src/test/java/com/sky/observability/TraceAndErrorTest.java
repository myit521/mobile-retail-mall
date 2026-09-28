package com.sky.observability;

import com.sky.exception.UserNotLoginException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.result.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TraceAndErrorTest {
    @AfterEach void clear() { MDC.clear(); }

    @Test
    void acceptsValidTraceAndReturnsItInResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "client-trace_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter().doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("client-trace_123");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void rejectsMalformedTraceAndDoesNotLeakMdcAcrossReusedThread() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletResponse first = invoke(filter, "bad trace\r\nforged");
        MockHttpServletResponse second = invoke(filter, null);
        assertThat(first.getHeader(TraceIdFilter.HEADER)).matches("[0-9a-f-]{36}");
        assertThat(second.getHeader(TraceIdFilter.HEADER)).matches("[0-9a-f-]{36}")
                .isNotEqualTo(first.getHeader(TraceIdFilter.HEADER));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void responseHeaderErrorBodyAndLogUseTheSameTrace() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        AtomicReference<String> loggedTrace = new AtomicReference<>();
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                error.set(new GlobalExceptionHandler().system(new RuntimeException("boom")).getBody());
                loggedTrace.set(appender.list.get(0).getMDCPropertyMap().get("traceId"));
            });
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(error.get().getTraceId())
                .isEqualTo(loggedTrace.get());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void errorsUseStableCodesAndCurrentTrace() {
        MDC.put("traceId", "trace-errors-1");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertError(handler.validation(new ConstraintViolationException(Collections.emptySet())), "VALIDATION_ERROR");
        assertError(handler.authentication(new UserNotLoginException("not logged in")), "AUTHENTICATION_ERROR");
        assertError(handler.permission(new SecurityException("denied")), "PERMISSION_DENIED");
        assertError(handler.conflict(new DataIntegrityViolationException("duplicate")), "CONFLICT");
        assertError(handler.system(new RuntimeException("boom")), "SYSTEM_ERROR");
    }

    @Test
    void authenticationFailureUsesHttp401AndNewErrorSchema() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthenticationProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new TraceIdFilter()).build();
        mvc.perform(get("/auth-probe").header(TraceIdFilter.HEADER, "client-trace-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.HEADER, "client-trace-401"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
                .andExpect(jsonPath("$.traceId").value("client-trace-401"));
    }

    @RestController
    static class AuthenticationProbeController {
        @GetMapping("/auth-probe")
        void fail() {
            throw new UserNotLoginException("login required");
        }
    }

    @Test
    void eventContextUsesOnlyAllowlistedValuesAndRestoresCallerContext() {
        MDC.put("traceId", "request-trace");
        try (MdcContext ignored = MdcContext.open(java.util.Map.of(
                "eventId", "event-1", "correlationId", "correlation-1", "orderId", 42L,
                "payload", "secret-payload"))) {
            assertThat(MDC.get("traceId")).isEqualTo("request-trace");
            assertThat(MDC.get("eventId")).isEqualTo("event-1");
            assertThat(MDC.get("correlationId")).isEqualTo("correlation-1");
            assertThat(MDC.get("orderId")).isEqualTo("42");
            assertThat(MDC.get("payload")).isNull();
        }
        assertThat(MDC.get("traceId")).isEqualTo("request-trace");
    }

    @Test
    void unsafeAsyncIdentifiersCannotForgeReadableLogContext() {
        try (MdcContext ignored = MdcContext.open(java.util.Map.of(
                "eventId", "event-1\r\nFORGED", "correlationId", "correlation-1\nFORGED",
                "orderId", "42\rFORGED"))) {
            assertThat(MDC.get("eventId")).doesNotContain("\r", "\n", "FORGED");
            assertThat(MDC.get("correlationId")).doesNotContain("\r", "\n", "FORGED");
            assertThat(MDC.get("orderId")).doesNotContain("\r", "\n", "FORGED");
        }
    }

    private MockHttpServletResponse invoke(TraceIdFilter filter, String trace) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (trace != null) request.addHeader(TraceIdFilter.HEADER, trace);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void assertError(ResponseEntity<ErrorResponse> response, String code) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(code);
        assertThat(response.getBody().getTraceId()).isEqualTo("trace-errors-1");
    }
}
