package com.atm.gateway.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealTimeSessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessionsByAccount = new ConcurrentHashMap<>();

    public void register(String accountNumber, WebSocketSession session) {
        sessionsByAccount.computeIfAbsent(accountNumber, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String accountNumber, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByAccount.get(accountNumber);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByAccount.remove(accountNumber);
            }
        }
    }

    public Set<WebSocketSession> getSessions(String accountNumber) {
        return sessionsByAccount.get(accountNumber);
    }
}