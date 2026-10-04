package com.atm.admin;

import java.math.BigDecimal;

public class Account {
    private long id;
    private String accountNumber;
    private String holderName;
    private BigDecimal balance;
    private String currency;
    private String role;
    private boolean active;
    private String status;
    private String createdAt;

    public Account() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFormattedBalance() {
        return String.format("%s %,.2f", currency != null ? currency : "USD", balance != null ? balance : BigDecimal.ZERO);
    }

    @Override
    public String toString() {
        return accountNumber + " - " + holderName + " (" + status + ")";
    }
}
