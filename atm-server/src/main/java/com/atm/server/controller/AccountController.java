package com.atm.server.controller;

import com.atm.server.dto.AmountRequest;
import com.atm.server.dto.TransactionResponse;
import com.atm.server.dto.TransferRequest;
import com.atm.server.service.BankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final BankingService bankingService;

    public AccountController(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    @GetMapping("/balance")
    public ResponseEntity<?> balance(Principal principal) {
        return ResponseEntity.ok(Map.of("balance", bankingService.checkBalance(principal.getName())));
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(Principal principal, @Valid @RequestBody AmountRequest req) {
        TransactionResponse tx = bankingService.deposit(principal.getName(), req.getAmount());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "balance", tx.balanceAfter(),
            "message", "Deposit successful"
        ));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(Principal principal, @Valid @RequestBody AmountRequest req) {
        try {
            TransactionResponse tx = bankingService.withdraw(principal.getName(), req.getAmount());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "balance", tx.balanceAfter(),
                "message", "Withdrawal successful"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(Principal principal, @Valid @RequestBody TransferRequest req) {
        try {
            String ref = bankingService.transfer(principal.getName(), req.getAmount(), req.getToAccountNumber());
            return ResponseEntity.ok(Map.of("success", true, "reference", ref, "message", "Transfer successful"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<TransactionResponse>> history(
            Principal principal,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(bankingService.history(principal.getName(), limit));
    }
}