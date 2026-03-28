package com.mall.websocket;

import com.mall.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级 WebSocket 服务器
 * 支持：并发安全、心跳机制、Token 鉴权、连接数限制
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {
    
    // 线程安全的会话存储
    private static final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();
    
    // 当前连接数
    private static final AtomicInteger connectionCount = new AtomicInteger(0);
    
    // 最大连接数限制
    private static final int MAX_CONNECTIONS = 1000;
    
    // 心跳超时时间（秒）
    private static final int HEARTBEAT_TIMEOUT = 90;
    
    // 存放每个连接的最后心跳时间
    private static final ConcurrentHashMap<String, Long> heartbeatMap = new ConcurrentHashMap<>();
    
    /**
     * 连接建立时的处理
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid, EndpointConfig config) {
        // 连接数限制
        if (connectionCount.get() >= MAX_CONNECTIONS) {
            log.warn("连接数已达上限 ({})，拒绝新连接", MAX_CONNECTIONS);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "服务器连接数已满"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }
        
        // Token 鉴权（从 URL 参数获取）
        String token = getTokenFromSession(session);
        if (!JwtUtil.verify(token)) {
            log.warn("WebSocket 连接鉴权失败，客户端 ID：{}", sid);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.POLICY_VIOLATION, "鉴权失败"));
            } catch (IOException e) {
                log.error("关闭连接失败", e);
            }
            return;
        }
        
        log.info("WebSocket 连接建立，客户端 ID：{}，当前连接数：{}", sid, connectionCount.incrementAndGet());
        sessionMap.put(sid, session);
        heartbeatMap.put(sid, System.currentTimeMillis());
    }
    
    /**
     * 收到消息时的处理（支持心跳）
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid, Session session) {
        // 更新心跳时间
        heartbeatMap.put(sid, System.currentTimeMillis());
        
        // 心跳响应
        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
                log.debug("心跳响应，客户端 ID：{}", sid);
            } catch (IOException e) {
                log.error("发送心跳响应失败，客户端 ID：{}", sid, e);
            }
            return;
        }
        
        // 业务消息处理（预留扩展）
        log.debug("收到客户端 {} 消息：{}", sid, message);
    }
    
    /**
     * 连接关闭时的处理
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket 连接关闭，客户端 ID：{}，当前连接数：{}", sid, connectionCount.decrementAndGet());
        sessionMap.remove(sid);
        heartbeatMap.remove(sid);
    }
    
    /**
     * 发生错误时的处理
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
        log.error("WebSocket 发生错误，客户端 ID：{}，错误：{}", sid, error.getMessage());
        // 尝试关闭连接
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.error("关闭连接失败，客户端 ID：{}", sid, e);
        }
    }
    
    /**
     * 发送消息给指定客户端
     * @param message 消息内容
     * @param sid 客户端 ID
     */
    public static void sendMessage(String message, String sid) {
        Session session = sessionMap.get(sid);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.info("发送消息给客户端 {}：{}", sid, message);
            } catch (IOException e) {
                log.error("发送消息失败，客户端 ID：{}，错误：{}", sid, e.getMessage());
                // 发送失败时移除会话
                sessionMap.remove(sid);
                heartbeatMap.remove(sid);
            }
        } else {
            log.warn("客户端 {} 不存在或连接已关闭", sid);
        }
    }
    
    /**
     * 群发消息给所有客户端
     * @param message 消息内容
     */
    public static void sendMessage(String message) {
        log.info("群发消息给 {} 个客户端：{}", sessionMap.size(), message);
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            Session session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("群发消息失败，客户端 ID：{}，错误：{}", entry.getKey(), e.getMessage());
                }
            }
        }
    }
    
    /**
     * 获取在线连接数
     */
    public static int getConnectionCount() {
        return connectionCount.get();
    }
    
    /**
     * 获取指定客户端的会话
     */
    public static Session getSession(String sid) {
        return sessionMap.get(sid);
    }
    
    /**
     * 优雅关闭所有连接
     */
    @PreDestroy
    public void preDestroy() {
        log.info("正在关闭所有 WebSocket 连接...");
        sessionMap.forEach((sid, session) -> {
            try {
                if (session != null && session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "服务器关闭"));
                    log.info("已关闭客户端 {} 的连接", sid);
                }
            } catch (IOException e) {
                log.error("关闭连接失败，客户端 ID：{}", sid, e);
            }
        });
        sessionMap.clear();
        heartbeatMap.clear();
        connectionCount.set(0);
        log.info("所有 WebSocket 连接已关闭");
    }
    
    /**
     * 从 Session 中获取 Token（URL 参数）
     */
    private String getTokenFromSession(Session session) {
        String queryString = session.getQueryString();
        if (queryString != null && queryString.contains("token=")) {
            String[] params = queryString.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }
}
