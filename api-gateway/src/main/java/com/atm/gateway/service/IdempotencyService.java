package com.atm.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-generated idempotency key guard. Stores the computed result keyed by
 * `accountNumber|idempotencyKey` for a short window so duplicate submissions
 * (network retries, double-taps) return the original result instead of
 * re-executing a financial operation.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final long WINDOW_MS = 30 * 60 * 1000L;

    private final Map<String, IdempotencyEntry> store = new ConcurrentHashMap<>();

    public boolean isDuplicate(String accountNumber, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String key = accountNumber + "|" + idempotencyKey;
        IdempotencyEntry existing = store.get(key);
        if (existing != null && existing.expiresAt > System.currentTimeMillis()) {
            return true;
        }
        if (existing != null) {
            store.remove(key);
        }
        return false;
    }

    public void recordResult(String accountNumber, String idempotencyKey, Object result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        String key = accountNumber + "|" + idempotencyKey;
        store.put(key, new IdempotencyEntry(System.currentTimeMillis() + WINDOW_MS, result));
        log.debug("Recorded idempotency result for {}", key);
    }

    public Object getResult(String accountNumber, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = accountNumber + "|" + idempotencyKey;
        IdempotencyEntry entry = store.get(key);
        if (entry == null || entry.expiresAt <= System.currentTimeMillis()) {
            return null;
        }
        return entry.result;
    }

    private record IdempotencyEntry(long expiresAt, Object result) {}
}