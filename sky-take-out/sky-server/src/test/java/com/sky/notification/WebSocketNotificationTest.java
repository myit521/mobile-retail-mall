package com.sky.notification;

import com.sky.auth.api.TokenSessionService;
import com.sky.notification.internal.WebSocketOrderNotificationAdapter;
import com.sky.notification.internal.websocket.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.io.IOException;
import java.time.*;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebSocketNotificationTest {
    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");

    @AfterEach void clearState() {
        WebSocketServer.clearConfiguration();
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test void actualAdminTicketShapeConnectsAndIdentityComesFromTicket() throws Exception {
        TokenSessionService auth = mock(TokenSessionService.class);
        when(auth.isAdminSessionValid("admin-session")).thenReturn(true);
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(auth, clock());
        WebSocketTicketService tickets = mock(WebSocketTicketService.class);
        when(tickets.consume("one-time-ticket")).thenReturn(Optional.of(principal("42", "admin-session", 600)));
        WebSocketServer.configure(registry, tickets);
        Session socket = session("one-time-ticket", mock(RemoteEndpoint.Basic.class));

        new WebSocketServer().onOpen(socket);

        assertThat(registry.contains("42", socket)).isTrue();
        verify(socket, never()).close(any(CloseReason.class));
    }

    @Test void missingUsedExpiredRevokedAndWrongAudienceTicketsNeverEnterRegistry() throws Exception {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(mock(TokenSessionService.class), clock());
        WebSocketTicketService tickets = mock(WebSocketTicketService.class);
        when(tickets.consume(any())).thenReturn(Optional.empty());
        WebSocketServer.configure(registry, tickets);
        for (String ticket : new String[]{null, "used", "expired", "revoked", "wrong-audience"}) {
            Session socket = session(ticket, mock(RemoteEndpoint.Basic.class));
            new WebSocketServer().onOpen(socket);
            verify(socket).close(any(CloseReason.class));
        }
        assertThat(registry.size()).isZero();
    }

    @Test void sendsOnSameSessionAreSerializedWithoutEviction() throws Exception {
        TokenSessionService auth = mock(TokenSessionService.class);
        when(auth.isAdminSessionValid("session")).thenReturn(true);
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(auth, clock());
        BlockingRemote remote = new BlockingRemote();
        Session socket = session(null, remote);
        registry.open(principal("7", "session", 60), socket);
        Thread first = new Thread(() -> registry.sendTo("7", "first"));
        Thread second = new Thread(() -> registry.sendTo("7", "second"));
        first.start(); assertThat(remote.entered.await(2, TimeUnit.SECONDS)).isTrue();
        second.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield();
        assertThat(second.getState()).isEqualTo(Thread.State.BLOCKED);
        assertThat(remote.overlap).isFalse();
        remote.release.countDown(); first.join(2000); second.join(2000);
        assertThat(remote.calls).hasValue(2);
        assertThat(registry.contains("7", socket)).isTrue();
    }

    @Test void revokedOrExpiredAuthenticationClosesWithoutSending() throws Exception {
        TokenSessionService auth = mock(TokenSessionService.class);
        when(auth.isAdminSessionValid("revoked")).thenReturn(false);
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(auth, clock());
        RemoteEndpoint.Basic revokedRemote = mock(RemoteEndpoint.Basic.class);
        Session revoked = session(null, revokedRemote);
        registry.open(principal("7", "revoked", 60), revoked);
        assertThat(registry.sendTo("7", "message")).isFalse();
        verify(revoked).close(any(CloseReason.class)); verifyNoInteractions(revokedRemote);

        RemoteEndpoint.Basic expiredRemote = mock(RemoteEndpoint.Basic.class);
        Session expired = session(null, expiredRemote);
        registry.open(new WebSocketPrincipal("8", "active", NOW.minusSeconds(1)), expired);
        assertThat(registry.sendTo("8", "message")).isFalse();
        verify(expired).close(any(CloseReason.class)); verifyNoInteractions(expiredRemote);
    }

    @Test void replacementClosesOldAndItsCallbackCannotRemoveNew() throws Exception {
        TokenSessionService auth = mock(TokenSessionService.class);
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry(auth, clock());
        Session oldSocket = session(null, mock(RemoteEndpoint.Basic.class));
        Session replacement = session(null, mock(RemoteEndpoint.Basic.class));
        registry.open(principal("7", "session", 60), oldSocket);
        registry.open(principal("7", "session", 60), replacement);
        registry.close(oldSocket);
        verify(oldSocket).close(any(CloseReason.class));
        assertThat(registry.contains("7", replacement)).isTrue();
    }

    @Test void notificationIsAfterCommitBestEffortAndRollbackDoesNotSend() {
        WebSocketSessionRegistry registry = mock(WebSocketSessionRegistry.class);
        doThrow(new IllegalStateException("failed")).when(registry).broadcast("paid");
        WebSocketOrderNotificationAdapter adapter = new WebSocketOrderNotificationAdapter(registry);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        adapter.broadcast("paid"); verifyNoInteractions(registry);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(registry).broadcast("paid");

        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        adapter.sendTo("paid", "7");
        TransactionSynchronizationManager.getSynchronizations().get(0)
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(registry, never()).sendTo(any(), any());
    }

    private Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    private WebSocketPrincipal principal(String id, String session, long seconds) {
        return new WebSocketPrincipal(id, session, NOW.plusSeconds(seconds));
    }
    private Session session(String ticket, RemoteEndpoint.Basic remote) {
        Session session = mock(Session.class);
        when(session.getRequestParameterMap()).thenReturn(ticket == null ? Collections.emptyMap()
                : Collections.singletonMap("ticket", Collections.singletonList(ticket)));
        when(session.getBasicRemote()).thenReturn(remote); when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test void endpointTemplateContainsEveryDeclaredPathParameter() throws NoSuchMethodException {
        String template = WebSocketServer.class.getAnnotation(ServerEndpoint.class).value();
        assertThat(template).isEqualTo("/ws");
        for (Method method : WebSocketServer.class.getDeclaredMethods()) {
            for (Annotation[] annotations : method.getParameterAnnotations()) {
                for (Annotation annotation : annotations) {
                    if (annotation instanceof PathParam) {
                        assertThat(template).contains("{" + ((PathParam) annotation).value() + "}");
                    }
                }
            }
        }
        assertThat(WebSocketServer.class.getDeclaredMethod("onMessage", String.class)).isNotNull();
    }

    private static final class BlockingRemote implements RemoteEndpoint.Basic {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean sending = new AtomicBoolean(), overlap = new AtomicBoolean();
        final AtomicInteger calls = new AtomicInteger();
        public void sendText(String text) {
            if (!sending.compareAndSet(false, true)) overlap.set(true);
            try { if (calls.incrementAndGet() == 1) { entered.countDown(); release.await(2, TimeUnit.SECONDS); } }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { sending.set(false); }
        }
        public void sendBinary(java.nio.ByteBuffer d) { throw new UnsupportedOperationException(); }
        public void sendText(String d, boolean l) { throw new UnsupportedOperationException(); }
        public void sendBinary(java.nio.ByteBuffer d, boolean l) { throw new UnsupportedOperationException(); }
        public java.io.OutputStream getSendStream() { throw new UnsupportedOperationException(); }
        public java.io.Writer getSendWriter() { throw new UnsupportedOperationException(); }
        public void sendObject(Object d) { throw new UnsupportedOperationException(); }
        public void setBatchingAllowed(boolean a) { } public boolean getBatchingAllowed() { return false; }
        public void flushBatch() { } public void sendPing(java.nio.ByteBuffer d) { }
        public void sendPong(java.nio.ByteBuffer d) { }
    }
}
