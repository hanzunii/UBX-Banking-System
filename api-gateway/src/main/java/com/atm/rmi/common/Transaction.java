package com.atm.rmi.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { TRANSFER, DEPOSIT, WITHDRAWAL }

    private String id;
    private String transactionRef;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private Type type;
    private String reference;
    private BigDecimal senderBalanceAfter;
    private BigDecimal receiverBalanceAfter;
    private LocalDateTime timestamp;

    public Transaction() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getFromAccountNumber() { return fromAccountNumber; }
    public void setFromAccountNumber(String fromAccountNumber) { this.fromAccountNumber = fromAccountNumber; }

    public String getToAccountNumber() { return toAccountNumber; }
    public void setToAccountNumber(String toAccountNumber) { this.toAccountNumber = toAccountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public BigDecimal getSenderBalanceAfter() { return senderBalanceAfter; }
    public void setSenderBalanceAfter(BigDecimal senderBalanceAfter) { this.senderBalanceAfter = senderBalanceAfter; }

    public BigDecimal getReceiverBalanceAfter() { return receiverBalanceAfter; }
    public void setReceiverBalanceAfter(BigDecimal receiverBalanceAfter) { this.receiverBalanceAfter = receiverBalanceAfter; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
