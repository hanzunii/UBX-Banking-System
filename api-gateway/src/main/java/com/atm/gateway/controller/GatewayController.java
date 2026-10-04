package com.atm.gateway.controller;

import com.atm.gateway.dto.*;
import com.atm.gateway.service.IdempotencyService;
import com.atm.gateway.service.RealtimePusher;
import com.atm.gateway.service.RmiBankingService;
import com.atm.rmi.common.Account;
import com.atm.rmi.common.Transaction;
import com.atm.rmi.exception.AuthenticationException;
import com.atm.rmi.exception.AccountLockedException;
import com.atm.gateway.security.JwtUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final RmiBankingService rmi;
    private final IdempotencyService idempotency;
    private final RealtimePusher pusher;
    private final JwtUtil jwtUtil;

    public GatewayController(RmiBankingService rmi, IdempotencyService idempotency,
                             RealtimePusher pusher, JwtUtil jwtUtil) {
        this.rmi = rmi;
        this.idempotency = idempotency;
        this.pusher = pusher;
        this.jwtUtil = jwtUtil;
    }

    // ---------- Auth ----------

    /**
     * Step 1 of MFA: validates the account credentials. On success, an OTP is
     * generated on the RMI core and a short-lived MFA token is returned so the
     * client can complete authentication in the verification step.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            rmi.preauthenticate(request.getAccountNumber(), request.getPin());
        } catch (AccountLockedException e) {
            return ResponseEntity.status(423).body(Map.of("error", e.getMessage()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
        Account account = rmi.getAccount(request.getAccountNumber());
        if (!account.isActive()) {
            String statusMsg = "Account is deactivated";
            if ("PENDING".equals(account.getStatus())) {
                statusMsg = "Account pending bank approval. Please wait for a bank administrator to approve your account.";
            } else if ("REJECTED".equals(account.getStatus())) {
                statusMsg = "Account registration was rejected by the bank.";
            }
            return ResponseEntity.status(403).body(Map.of("error", statusMsg));
        }
        // Generate and dispatch OTP via the RMI core
        String otp = rmi.generateOtp(account.getAccountNumber());
        String mfaToken = jwtUtil.generateMfaToken(account.getAccountNumber(), account.getRole());
        log.info("MFA OTP dispatched for account {} (demo delivery: {})", request.getAccountNumber(), otp);
        Map<String, Object> resp = new HashMap<>();
        resp.put("requiresOtp", true);
        resp.put("mfaToken", mfaToken);
        resp.put("accountNumber", account.getAccountNumber());
        resp.put("holderName", account.getHolderName());
        resp.put("otpTtlSeconds", 300);
        resp.put("demoOtp", otp);
        return ResponseEntity.ok(resp);
    }

    /**
     * Step 2 of MFA: verifies the OTP and, on success, issues the access + refresh tokens.
     */
    @PostMapping("/auth/mfa/verify")
    public ResponseEntity<?> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        String accountNumber;
        try {
            var claims = jwtUtil.parseToken(request.getMfaToken());
            if (!"mfa".equals(claims.get("type", String.class)) ||
                    claims.getExpiration().before(new Date())) {
                return ResponseEntity.status(401).body(Map.of("error", "MFA token invalid or expired"));
            }
            accountNumber = claims.get("accountNumber", String.class);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid MFA token"));
        }

        boolean ok = rmi.verifyOtp(accountNumber, request.getOtp());
        if (!ok) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired OTP"));
        }

        Account account = rmi.getAccount(accountNumber);
        String role = account.getRole() == null ? "USER" : account.getRole();
        String accessToken = jwtUtil.generateToken(accountNumber, role);
        String refreshToken = jwtUtil.generateRefreshToken(accountNumber, role);
        LoginResponse response = new LoginResponse(
                accessToken, refreshToken, accountNumber,
                account.getHolderName(), role, account.getCurrency());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        boolean ok = rmi.register(request.getAccountNumber(), request.getHolderName(),
                request.getPhone(), request.getPin(), request.getRole());
        if (!ok) {
            return ResponseEntity.status(409).body(Map.of("error", "Account number already exists"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "accountNumber", request.getAccountNumber(),
                "status", "PENDING",
                "message", "Account registered successfully. A bank administrator will review and approve your account. You will be able to log in once approved."));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            var claims = jwtUtil.parseToken(request.getRefreshToken());
            if (!"refresh".equals(claims.get("type", String.class)) ||
                    claims.getExpiration().before(new Date())) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
            }
            String accountNumber = claims.get("accountNumber", String.class);
            String role = claims.get("role", String.class);
            String newAccess = jwtUtil.generateToken(accountNumber, role);
            String newRefresh = jwtUtil.generateRefreshToken(accountNumber, role);
            return ResponseEntity.ok(Map.of(
                    "accessToken", newAccess,
                    "refreshToken", newRefresh,
                    "accountNumber", accountNumber,
                    "role", role));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }
    }

    /**
     * Re-authentication gate for sensitive actions (transfers, viewing history).
     * Validates the caller's PIN against the RMI core without issuing a new session.
     * On success the caller may proceed; on failure it consumes a failed-attempt
     * budget (same lockout rules as login).
     */
    @PostMapping("/auth/verify-pin")
    public ResponseEntity<?> verifyPin(@Valid @RequestBody VerifyPinRequest request,
                                       @RequestAttribute("accountNumber") String accountNumber) {
        try {
            rmi.preauthenticate(accountNumber, request.getPin());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (AccountLockedException e) {
            return ResponseEntity.status(423).body(Map.of("error", e.getMessage()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            rmi.logout(auth.substring(7));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ---------- Accounts ----------

    @GetMapping("/accounts/me")
    public ResponseEntity<?> me(@RequestAttribute("accountNumber") String accountNumber) {
        Account account = rmi.getAccount(accountNumber);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/system/config")
    public ResponseEntity<?> systemConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("systemStatus", "OPERATIONAL");
        config.put("maintenanceMode", false);
        config.put("currency", "USD");
        config.put("currencyRates", Map.of(
                "USD", 1.00,
                "EUR", 0.92,
                "GBP", 0.79,
                "JPY", 149.50,
                "MMK", 3300.00
        ));
        config.put("defaultCurrency", "USD");
        config.put("supportedCurrencies", List.of("USD", "EUR", "GBP", "JPY", "MMK"));
        config.put("accessTokenTtlMinutes", jwtUtil.getExpirationMs() / 60000);
        config.put("transferFee", 0.00);
        config.put("dailyTransferLimit", 10000.00);
        config.put("maxTransferAmount", 5000.00);
        config.put("systemName", "Distributed RMI Banking System");
        config.put("version", "2.0.0");
        config.put("serverTime", new Date().toInstant().toString());
        return ResponseEntity.ok(config);
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "UP");
        resp.put("service", "api-gateway");
        resp.put("rmiRegistry", "1099");
        resp.put("rmiStatus", "CONNECTED");
        resp.put("services", List.of("AuthService", "AccountService", "TransactionService", "AdminService"));
        resp.put("timestamp", new Date().toInstant().toString());
        return ResponseEntity.ok(resp);
    }

    // ---------- Admin ----------

    private void requireAdmin(String role, String accountNumber) {
        if (!"ADMIN".equals(role) && !"MANAGER".equals(role)) {
            throw new com.atm.rmi.exception.AuthenticationException("Insufficient privileges: ADMIN role required. Current role: " + role);
        }
    }

    @GetMapping("/admin/pending")
    public ResponseEntity<?> listPending(@RequestAttribute("accountNumber") String callerAccount,
                                         @RequestAttribute("role") String role) {
        try {
            requireAdmin(role, callerAccount);
            return ResponseEntity.ok(rmi.listPendingAccounts());
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/accounts")
    public ResponseEntity<?> listActive(@RequestAttribute("accountNumber") String callerAccount,
                                        @RequestAttribute("role") String role) {
        try {
            requireAdmin(role, callerAccount);
            return ResponseEntity.ok(rmi.listActiveAccounts());
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/{accountNumber}/approve")
    public ResponseEntity<?> approve(@PathVariable String accountNumber,
                                     @RequestAttribute("accountNumber") String callerAccount,
                                     @RequestAttribute("role") String role) {
        try {
            requireAdmin(role, callerAccount);
            return ResponseEntity.ok(rmi.approveAccount(callerAccount, accountNumber));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/{accountNumber}/reject")
    public ResponseEntity<?> reject(@PathVariable String accountNumber,
                                    @RequestAttribute("accountNumber") String callerAccount,
                                    @RequestAttribute("role") String role) {
        try {
            requireAdmin(role, callerAccount);
            return ResponseEntity.ok(rmi.rejectAccount(callerAccount, accountNumber));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/admin/{accountNumber}/credit")
    public ResponseEntity<?> credit(@PathVariable String accountNumber,
                                    @Valid @RequestBody AdminCreditRequest request,
                                    @RequestAttribute("accountNumber") String callerAccount,
                                    @RequestAttribute("role") String role,
                                    @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        try {
            requireAdmin(role, callerAccount);

            if (idempotency.isDuplicate(callerAccount, idempotencyKey)) {
                return ResponseEntity.ok(idempotency.getResult(callerAccount, idempotencyKey));
            }

            Account updated = rmi.creditAccount(callerAccount, accountNumber, request.getAmount(), request.getReference());
            Map<String, Object> resp = Map.of(
                    "account", updated,
                    "status", "SUCCESS",
                    "creditedAmount", request.getAmount());
            idempotency.recordResult(callerAccount, idempotencyKey, resp);

            pusher.pushAccountUpdated(accountNumber, updated.getBalance(), updated.getCurrency(),
                    "ADMIN_CREDIT", "Funds credited by bank admin");

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------- Transactions ----------

    /**
     * Look up the exact registered holder name for a recipient account number.
     * Ensures one account number maps to exactly one name (as registered),
     * so the sender can verify the correct recipient before transferring.
     */
    @GetMapping("/accounts/lookup/{accountNumber}")
    public ResponseEntity<?> lookup(@PathVariable String accountNumber,
                                    @RequestAttribute("accountNumber") String callerAccount) {
        if (!accountNumber.matches("\\d{3,20}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid account number"));
        }
        try {
            Account account = rmi.getAccount(accountNumber);
            return ResponseEntity.ok(Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "holderName", account.getHolderName(),
                    "active", account.isActive(),
                    "status", account.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Account not found"));
        }
    }

    @PostMapping("/transactions/transfer")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest request,
                                      @RequestAttribute("accountNumber") String senderAccount,
                                      @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (senderAccount.equals(request.getToAccountNumber())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot transfer to the same account"));
        }

        if (idempotency.isDuplicate(senderAccount, idempotencyKey)) {
            Object cached = idempotency.getResult(senderAccount, idempotencyKey);
            log.info("Idempotent request replay suppressed for key={}", idempotencyKey);
            return ResponseEntity.ok(cached);
        }

        Transaction txn = rmi.transfer(senderAccount, request.getToAccountNumber(),
                request.getAmount(), request.getReference());

        Map<String, Object> resp = new HashMap<>();
        resp.put("transaction", txn);
        resp.put("status", "SUCCESS");

        idempotency.recordResult(senderAccount, idempotencyKey, resp);

        // Real-time push to both parties: sender sees its new balance, receiver sees credited funds.
        pusher.pushAccountUpdated(senderAccount, txn.getSenderBalanceAfter(), "USD",
                "TRANSFER_OUT", "Transfer completed");
        pusher.pushAccountUpdated(request.getToAccountNumber(), txn.getReceiverBalanceAfter(), "USD",
                "TRANSFER_IN", "Funds received");

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/transactions/history")
    public ResponseEntity<?> history(@RequestAttribute("accountNumber") String accountNumber,
                                     @RequestParam(defaultValue = "20") int limit) {
        if (limit <= 0) limit = 20;
        if (limit > 200) limit = 200;
        List<Transaction> txns = rmi.getHistory(accountNumber, limit);
        return ResponseEntity.ok(Map.of("transactions", txns));
    }
}