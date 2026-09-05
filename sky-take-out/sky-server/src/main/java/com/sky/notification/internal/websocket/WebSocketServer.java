package com.sky.notification.internal.websocket;

import com.sky.notification.api.OrderNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;

@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer implements OrderNotificationPort {
    //存放会话对象
    private static Map<String, Session> sessionMap =new HashMap();
    
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("WebSocket连接建立，客户端ID：{}", sid);
        sessionMap.put(sid, session);
    }
    
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        // 收到消息时的处理
    }
    
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket连接关闭，客户端ID：{}", sid);
        sessionMap.remove(sid);
    }
    
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket发生错误", error);
    }
    /**
     * 发送消息给指定客户端
     * @param message 消息内容
     * @param sid 客户端ID
     */
    public static void sendMessage(String message, String sid) {
        Session session = sessionMap.get(sid);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                log.info("发送消息给客户端 {}：{}", sid, message);
            } catch (Exception e) {
                log.error("发送消息失败，客户端ID：{}", sid, e);
            }
        } else {
            log.warn("客户端 {} 不存在或连接已关闭", sid);
        }
    }
    /**
     * 群发消息给所有客户端
     * @param message 消息内容v
     */
    public static void sendMessage(String message) {
        log.info("群发消息给 {} 个客户端：{}", sessionMap.size(), message);
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            Session session = entry.getValue();
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (Exception e) {
                    log.error("群发消息失败，客户端ID：{}", entry.getKey(), e);
                }
            }
        }
    }

    @Override
    public void broadcast(String message) {
        sendMessage(message);
    }

    @Override
    public void sendTo(String message, String clientId) {
        sendMessage(message, clientId);
    }
 }
