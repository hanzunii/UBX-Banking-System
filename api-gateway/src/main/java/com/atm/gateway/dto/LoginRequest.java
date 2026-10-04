package com.atm.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LoginRequest {

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "\\d{3,20}", message = "Account number must be 3-20 digits")
    private String accountNumber;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "\\d{4}", message = "PIN must be 4 digits")
    private String pin;

    private String deviceId;

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getDeviceId() { return deviceId == null ? "web-" + System.currentTimeMillis() : deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}