package com.atm.common;

import java.io.Serializable;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accountNumber;
    private String pin;
    private String holderName;
    private double balance;

    public Account(String accountNumber, String pin, String holderName, double balance) {
        this.accountNumber = accountNumber;
        this.pin = pin;
        this.holderName = holderName;
        this.balance = balance;
    }

    // Getters & Setters
    public String getAccountNumber() { return accountNumber; }
    public String getPin() { return pin; }
    public String getHolderName() { return holderName; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}