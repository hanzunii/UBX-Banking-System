package com.atm.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResponse {

    @JsonProperty("accessToken")
    private String accessToken;

    @JsonProperty("refreshToken")
    private String refreshToken;

    private String accountNumber;
    private String holderName;
    private String role;
    private String currency;

    public LoginResponse() {}

    public LoginResponse(String accessToken, String refreshToken, String accountNumber,
                         String holderName, String role, String currency) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.role = role;
        this.currency = currency;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}