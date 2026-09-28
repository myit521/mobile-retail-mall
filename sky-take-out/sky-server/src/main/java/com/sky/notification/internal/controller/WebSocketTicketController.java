package com.sky.notification.internal.controller;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.notification.internal.websocket.WebSocketTicketService;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/admin/notifications")
public class WebSocketTicketController {
    private final WebSocketTicketService tickets;
    private final JwtProperties jwtProperties;

    public WebSocketTicketController(WebSocketTicketService tickets, JwtProperties jwtProperties) {
        this.tickets = tickets;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/websocket-ticket")
    public ResponseEntity<Result<Map<String, String>>> issue(HttpServletRequest request) {
        Long employeeId = BaseContext.getCurrentId();
        Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(),
                request.getHeader(jwtProperties.getAdminTokenName()));
        long claimedEmployeeId = Long.parseLong(String.valueOf(claims.get(JwtClaimsConstant.EMP_ID)));
        if (employeeId == null || employeeId != claimedEmployeeId) throw new IllegalStateException("Invalid admin identity");
        String adminSessionId = String.valueOf(claims.get(JwtClaimsConstant.SESSION_ID));
        Instant expiresAt = claims.getExpiration().toInstant();
        Result<Map<String, String>> body = Result.success(Collections.singletonMap("ticket",
                tickets.issue(employeeId, adminSessionId, expiresAt)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
