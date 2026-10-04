package com.atm.gateway.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class TransferRequest {

    @NotBlank(message = "Recipient account is required")
    @Pattern(regexp = "\\d{3,20}", message = "Recipient account number must be 3-20 digits")
    private String toAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 12, fraction = 2, message = "Amount has too many digits")
    private BigDecimal amount;

    @Size(max = 100, message = "Reference too long")
    private String reference;

    public String getToAccountNumber() { return toAccountNumber; }
    public void setToAccountNumber(String toAccountNumber) { this.toAccountNumber = toAccountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReference() { return reference == null ? "" : reference; }
    public void setReference(String reference) { this.reference = reference; }
}