package com.atm.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_tx_account", columnList = "accountNumber")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 12)
    private String type;   // CREDIT / DEBIT / TRANSFER_IN / TRANSFER_OUT

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private double balanceAfter;

    @Column(length = 20)
    private String counterparty; // "CASH" or other account number

    @Column(length = 200)
    private String reference;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Transaction() {}

    public Transaction(String accountNumber, String type, double amount, double balanceAfter,
                       String counterparty, String reference) {
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.counterparty = counterparty;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getCounterparty() { return counterparty; }
    public String getReference() { return reference; }
    public Instant getCreatedAt() { return createdAt; }
}