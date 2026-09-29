package com.sky.notification;

import com.sky.auth.api.TokenSessionService;
import com.sky.notification.internal.websocket.*;
import com.sky.notification.internal.controller.WebSocketTicketController;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSocketTicketServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");

    @Test void randomAdminTicketIsShortLivedAndConsumedOnce() {
        WebSocketTicketStore store = mock(WebSocketTicketStore.class);
        TokenSessionService sessions = mock(TokenSessionService.class);
        WebSocketTicketProperties props = new WebSocketTicketProperties(); props.setTicketTtl(Duration.ofSeconds(30));
        WebSocketTicketService service = new WebSocketTicketService(store, sessions, props, Clock.fixed(NOW, ZoneOffset.UTC));
        when(store.take(anyString())).thenReturn(Optional.of(WebSocketTicketStore.Record.admin(42L, "s", NOW.plusSeconds(600))));
        when(sessions.isAdminSessionValid("s")).thenReturn(true);
        String first = service.issue(42L, "s", NOW.plusSeconds(600));
        String second = service.issue(42L, "s", NOW.plusSeconds(600));
        assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(32);
        verify(store).put(eq(first), any(), eq(Duration.ofSeconds(30)));
        assertThat(service.consume(first)).contains(new WebSocketPrincipal("42", "s", NOW.plusSeconds(600)));
        verify(store).take(first);
    }

    @Test void usedExpiredRevokedAndWrongAudienceAreRejected() {
        WebSocketTicketStore store = mock(WebSocketTicketStore.class);
        TokenSessionService sessions = mock(TokenSessionService.class);
        WebSocketTicketProperties props = new WebSocketTicketProperties(); props.setTicketTtl(Duration.ofSeconds(30));
        WebSocketTicketService service = new WebSocketTicketService(store, sessions, props, Clock.fixed(NOW, ZoneOffset.UTC));
        when(store.take("used")).thenReturn(Optional.empty());
        when(store.take("expired")).thenReturn(Optional.of(WebSocketTicketStore.Record.admin(1L,"s",NOW.minusSeconds(1))));
        when(store.take("revoked")).thenReturn(Optional.of(WebSocketTicketStore.Record.admin(1L,"revoked",NOW.plusSeconds(1))));
        when(store.take("wrong")).thenReturn(Optional.of(new WebSocketTicketStore.Record("user",1L,"s",NOW.plusSeconds(1))));
        when(sessions.isAdminSessionValid("s")).thenReturn(true);
        assertThat(service.consume("used")).isEmpty(); assertThat(service.consume("expired")).isEmpty();
        assertThat(service.consume("revoked")).isEmpty(); assertThat(service.consume("wrong")).isEmpty();
    }
    @AfterEach void clearContext() { BaseContext.clear(); }

    @Test void authenticatedAdminHttpEndpointMintsTicketFromServerValidatedIdentity() {
        WebSocketTicketService tickets = mock(WebSocketTicketService.class);
        JwtProperties jwt = new JwtProperties();
        jwt.setAdminSecretKey("admin-ticket-test-secret-which-is-long-enough");
        jwt.setAdminTokenName("token");
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 42L);
        claims.put(JwtClaimsConstant.EMP_ROLE, "admin");
        claims.put(JwtClaimsConstant.SESSION_ID, "admin-session");
        String token = JwtUtil.createJWT(jwt.getAdminSecretKey(), 60000, claims);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("token")).thenReturn(token);
        when(tickets.issue(eq(42L), eq("admin-session"), any(Instant.class))).thenReturn("short-ticket");
        BaseContext.setCurrentId(42L);

        org.springframework.http.ResponseEntity<com.sky.result.Result<Map<String, String>>> response =
                new WebSocketTicketController(tickets, jwt).issue(request);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
        assertThat(response.getBody().getData())
                .containsEntry("ticket", "short-ticket");
    }
}
