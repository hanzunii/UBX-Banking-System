package com.atm.rmi.server;

import com.atm.rmi.common.Account;
import com.atm.rmi.common.Transaction;
import com.atm.rmi.service.AuthServiceImpl;
import com.atm.rmi.service.AccountServiceImpl;
import com.atm.rmi.service.TransactionServiceImpl;
import com.atm.rmi.service.AdminServiceImpl;
import com.atm.rmi.exception.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RMI Bridge Controller.
 * This is the translation boundary between the Node.js gateway and the RMI remote objects.
 * The gateway invokes these endpoints which in turn call the RMI service implementations,
 * preserving the core business logic in the RMI layer with explicit thread locking.
 */
@RestController
@RequestMapping("/api/v1/bridge")
public class RmiBridgeController {

    @Autowired
    private AuthServiceImpl authService;

    @Autowired
    private AccountServiceImpl accountService;

    @Autowired
    private TransactionServiceImpl transactionService;

    @Autowired
    private AdminServiceImpl adminService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String accountNumber = body.get("accountNumber");
            String pin = body.get("pin");
            String deviceId = body.getOrDefault("deviceId", "unknown");
            String token = authService.login(accountNumber, pin, deviceId);
            Account account = accountService.getAccountDetails(accountNumber);
            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("account", account);
            return ResponseEntity.ok(resp);
        } catch (AccountLockedException e) {
            return ResponseEntity.status(423).body(error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            boolean ok = authService.register(
                    body.get("accountNumber"),
                    body.get("holderName"),
                    body.get("phone"),
                    body.get("pin"),
                    body.get("role")
            );
            if (ok) {
                return ResponseEntity.ok(Map.of("success", true));
            }
            return ResponseEntity.status(409).body(error("Account number already exists"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<?> getAccount(@PathVariable String accountNumber) {
        try {
            Account account = accountService.getAccountDetails(accountNumber);
            return ResponseEntity.ok(account);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(error(e.getMessage()));
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> body) {
        try {
            String from = (String) body.get("fromAccountNumber");
            String to = (String) body.get("toAccountNumber");
            BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
            String reference = (String) body.getOrDefault("reference", "");
            Transaction txn = transactionService.transfer(from, to, amount, reference);
            return ResponseEntity.ok(txn);
        } catch (InsufficientBalanceException e) {
            return ResponseEntity.status(422).body(error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> body) {
        try {
            String accountNumber = (String) body.get("accountNumber");
            BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
            Transaction txn = transactionService.deposit(accountNumber, amount);
            return ResponseEntity.ok(txn);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> body) {
        try {
            String accountNumber = (String) body.get("accountNumber");
            BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
            String pin = (String) body.get("pin");
            Transaction txn = transactionService.withdraw(accountNumber, amount, pin);
            return ResponseEntity.ok(txn);
        } catch (InsufficientBalanceException e) {
            return ResponseEntity.status(422).body(error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<?> history(@PathVariable String accountNumber,
                                     @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Transaction> txns = transactionService.getHistory(accountNumber, limit);
            return ResponseEntity.ok(txns);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        try {
            authService.logout(body.getOrDefault("token", ""));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @GetMapping("/admin/pending")
    public ResponseEntity<?> listPending(@RequestHeader(value = "X-Admin-Account", required = false) String adminAccount) {
        try {
            if (adminAccount == null || adminAccount.isEmpty()) return ResponseEntity.status(400).body(error("X-Admin-Account header required"));
            return ResponseEntity.ok(adminService.listPendingAccounts());
        } catch (Exception e) {
            return ResponseEntity.status(403).body(error(e.getMessage()));
        }
    }

    @GetMapping("/admin/accounts")
    public ResponseEntity<?> listActive(@RequestHeader(value = "X-Admin-Account", required = false) String adminAccount) {
        try {
            if (adminAccount == null || adminAccount.isEmpty()) return ResponseEntity.status(400).body(error("X-Admin-Account header required"));
            return ResponseEntity.ok(adminService.listActiveAccounts());
        } catch (Exception e) {
            return ResponseEntity.status(403).body(error(e.getMessage()));
        }
    }

    @PostMapping("/admin/{accountNumber}/approve")
    public ResponseEntity<?> approve(@PathVariable String accountNumber,
                                     @RequestHeader(value = "X-Admin-Account", required = false) String adminAccount) {
        try {
            if (adminAccount == null || adminAccount.isEmpty()) return ResponseEntity.status(400).body(error("X-Admin-Account header required"));
            return ResponseEntity.ok(adminService.approveAccount(adminAccount, accountNumber));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @PostMapping("/admin/{accountNumber}/reject")
    public ResponseEntity<?> reject(@PathVariable String accountNumber,
                                    @RequestHeader(value = "X-Admin-Account", required = false) String adminAccount) {
        try {
            if (adminAccount == null || adminAccount.isEmpty()) return ResponseEntity.status(400).body(error("X-Admin-Account header required"));
            return ResponseEntity.ok(adminService.rejectAccount(adminAccount, accountNumber));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @PostMapping("/admin/{accountNumber}/credit")
    public ResponseEntity<?> credit(@PathVariable String accountNumber,
                                    @RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "X-Admin-Account", required = false) String adminAccount) {
        try {
            if (adminAccount == null || adminAccount.isEmpty()) return ResponseEntity.status(400).body(error("X-Admin-Account header required"));
            java.math.BigDecimal amount = new java.math.BigDecimal(String.valueOf(body.get("amount")));
            String reference = (String) body.getOrDefault("reference", "Bank admin credit");
            return ResponseEntity.ok(adminService.creditAccount(adminAccount, accountNumber, amount, reference));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(error(e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "UP");
        resp.put("rmiRegistry", "1099");
        resp.put("services", List.of("AuthService", "AccountService", "TransactionService", "AdminService"));
        resp.put("systemId", "RMI-CORE-" + System.currentTimeMillis());
        return ResponseEntity.ok(resp);
    }

    private Map<String, String> error(String message) {
        Map<String, String> m = new HashMap<>();
        m.put("error", message);
        return m;
    }
}
