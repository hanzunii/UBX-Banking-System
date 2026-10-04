package com.atm.server.dto;

public class AuthResponse {

    private String token;
    private String accountNumber;
    private String phone;
    private String holderName;
    private double balance;

    public AuthResponse(String token, String accountNumber, String phone, String holderName, double balance) {
        this.token = token;
        this.accountNumber = accountNumber;
        this.phone = phone;
        this.holderName = holderName;
        this.balance = balance;
    }

    public String getToken() { return token; }
    public String getAccountNumber() { return accountNumber; }
    public String getPhone() { return phone; }
    public String getHolderName() { return holderName; }
    public double getBalance() { return balance; }
}