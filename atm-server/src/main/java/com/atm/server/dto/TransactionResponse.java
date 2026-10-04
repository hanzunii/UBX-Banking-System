package com.atm.server.dto;

import java.time.Instant;

public record TransactionResponse(
    Long id,
    String type,
    double amount,
    double balanceAfter,
    String counterparty,
    String reference,
    Instant createdAt
) {}