package com.atm.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[0-9]+$", message = "Account number must be numeric")
    private String accountNumber;

    @NotBlank
    @Pattern(regexp = "^[0-9]{7,15}$", message = "Phone must be 7-15 digits")
    private String phone;

    @NotBlank
    @Size(min = 2, max = 100)
    private String holderName;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$", message = "PIN must be exactly 4 digits")
    private String pin;

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
}