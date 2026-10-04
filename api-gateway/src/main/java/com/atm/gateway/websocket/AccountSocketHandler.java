package com.atm.gateway.websocket;

import com.atm.gateway.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AccountSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountSocketHandler.class);

    private final RealTimeSessionRegistry registry;
    private final JwtUtil jwtUtil;

    public AccountSocketHandler(RealTimeSessionRegistry registry, JwtUtil jwtUtil) {
        this.registry = registry;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // No auth yet; client must send {"type":"AUTH","token":"<jwt>"}
        session.sendMessage(new TextMessage("{\"type\":\"WELCOME\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            String token = extractToken(payload);
            if (token == null || !jwtUtil.isValid(token)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized"));
                return;
            }
            String accountNumber = jwtUtil.extractAccountNumber(token);
            String type = jwtUtil.parseToken(token).get("type", String.class);
            if ("access".equals(type)) {
                registry.register(accountNumber, session);
                session.getAttributes().put("accountNumber", accountNumber);
                session.sendMessage(new TextMessage("{\"type\":\"AUTH_OK\",\"accountNumber\":\"" + accountNumber + "\"}"));
                log.info("Real-time session registered for account {}", accountNumber);
            } else {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token type"));
            }
        } catch (Exception e) {
            log.warn("WebSocket auth failed: {}", e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object acct = session.getAttributes().get("accountNumber");
        if (acct != null) {
            registry.unregister(String.valueOf(acct), session);
        }
    }

    private String extractToken(String payload) {
        int idx = payload.indexOf("\"token\"");
        if (idx < 0) return null;
        int c1 = payload.indexOf('"', idx + 7);
        if (c1 < 0) return null;
        int c2 = payload.indexOf('"', c1 + 1);
        if (c2 < 0) return null;
        return payload.substring(c1 + 1, c2);
    }
}