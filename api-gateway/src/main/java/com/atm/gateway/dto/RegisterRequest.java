package com.atm.gateway.dto;

import jakarta.validation.constraints.*;

public class RegisterRequest {

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "\\d{3,20}", message = "Account number must be 3-20 digits")
    private String accountNumber;

    @NotBlank(message = "Holder name is required")
    @Size(max = 100, message = "Holder name too long")
    private String holderName;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "\\+?\\d{7,15}", message = "Phone must be 7-15 digits (optionally +)")
    private String phone;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "\\d{4}", message = "PIN must be 4 digits")
    private String pin;

    private String role = "USER";

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}