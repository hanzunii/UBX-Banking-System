package com.atm.gateway.service;

import com.atm.gateway.websocket.RealTimeSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class RealtimePusher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePusher.class);

    private final RealTimeSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public RealtimePusher(RealTimeSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * Pushes an ACCOUNT_UPDATED event to all live sessions of the given accounts,
     * keeping sender and receiver UIs in sync without manual refresh.
     */
    public void pushAccountUpdated(String accountNumber, BigDecimal newBalance, String currency,
                                   String eventType, String message) {
        Set<WebSocketSession> sessions = registry.getSessions(accountNumber);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No live session for account {}", accountNumber);
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", "ACCOUNT_UPDATED",
                "eventType", eventType,
                "accountNumber", accountNumber,
                "balance", newBalance,
                "currency", currency,
                "message", message,
                "timestamp", Instant.now().toString()
        );
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            } catch (Exception e) {
                log.warn("Failed to push event to session", e);
            }
        }
    }
}