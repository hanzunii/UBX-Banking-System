package com.atm.gateway.service;

import com.atm.rmi.common.Account;
import com.atm.rmi.common.AccountServiceRemote;
import com.atm.rmi.common.AdminServiceRemote;
import com.atm.rmi.common.AuthServiceRemote;
import com.atm.rmi.common.Transaction;
import com.atm.rmi.common.TransactionServiceRemote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Gateway-side facade that invokes the RMI Core Server remote objects over
 * the RMI registry. All core business logic (locking, ACID) lives in the
 * RMI server; this layer only transacts with the remote stubs.
 */
@Service
public class RmiBankingService {

    private static final Logger log = LoggerFactory.getLogger(RmiBankingService.class);

    private final AuthServiceRemote authService;
    private final AccountServiceRemote accountService;
    private final TransactionServiceRemote transactionService;
    private final AdminServiceRemote adminService;
    private final String rmiHost;
    private final int rmiPort;

    public RmiBankingService(AuthServiceRemote authService,
                             AccountServiceRemote accountService,
                             TransactionServiceRemote transactionService,
                             AdminServiceRemote adminService,
                             @Value("${rmi.host:localhost}") String rmiHost,
                             @Value("${rmi.port:1099}") int rmiPort) {
        this.authService = authService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.adminService = adminService;
        this.rmiHost = rmiHost;
        this.rmiPort = rmiPort;
    }

    public String login(String accountNumber, String pin, String deviceId) {
        return invoke("AuthService", () -> authService.login(accountNumber, pin, deviceId));
    }

    public void preauthenticate(String accountNumber, String pin) {
        invoke("AuthService", () -> {
            authService.preauthenticate(accountNumber, pin);
            return null;
        });
    }

    public String generateOtp(String accountNumber) {
        return invoke("AuthService", () -> authService.generateOtp(accountNumber));
    }

    public boolean verifyOtp(String accountNumber, String otp) {
        return invoke("AuthService", () -> authService.verifyOtp(accountNumber, otp));
    }

    public boolean register(String accountNumber, String holderName, String phone, String pin, String role) {
        return invoke("AuthService", () -> authService.register(accountNumber, holderName, phone, pin, role));
    }

    public void logout(String token) {
        invoke("AuthService", () -> {
            authService.logout(token);
            return null;
        });
    }

    public Account getAccount(String accountNumber) {
        return invoke("AccountService", () -> accountService.getAccountDetails(accountNumber));
    }

    public Transaction transfer(String from, String to, BigDecimal amount, String reference) {
        return invoke("TransactionService", () -> transactionService.transfer(from, to, amount, reference));
    }

    public Transaction deposit(String accountNumber, BigDecimal amount) {
        return invoke("TransactionService", () -> transactionService.deposit(accountNumber, amount));
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount, String pin) {
        return invoke("TransactionService", () -> transactionService.withdraw(accountNumber, amount, pin));
    }

    public List<Transaction> getHistory(String accountNumber, int limit) {
        return invoke("TransactionService", () -> transactionService.getHistory(accountNumber, limit));
    }

    // ---------- Admin ----------

    public List<Account> listPendingAccounts() {
        return invoke("AdminService", () -> adminService.listPendingAccounts());
    }

    public List<Account> listActiveAccounts() {
        return invoke("AdminService", () -> adminService.listActiveAccounts());
    }

    public Account approveAccount(String adminAccountNumber, String accountNumber) {
        return invoke("AdminService", () -> adminService.approveAccount(adminAccountNumber, accountNumber));
    }

    public Account rejectAccount(String adminAccountNumber, String accountNumber) {
        return invoke("AdminService", () -> adminService.rejectAccount(adminAccountNumber, accountNumber));
    }

    public Account creditAccount(String adminAccountNumber, String accountNumber,
                                  BigDecimal amount, String reference) {
        return invoke("AdminService", () -> adminService.creditAccount(adminAccountNumber, accountNumber, amount, reference));
    }

    /**
     * Single choke point for every remote call, so each crossing of the RMI
     * boundary is logged with its target service and round-trip time. This is
     * the distributed hop - without it the gateway looks like it computed the
     * answers locally.
     */
    private <T> T invoke(String service, RemoteCall<T> call) {
        long startNanos = System.nanoTime();
        try {
            T result = call.call();
            log.info(rmiLine("RMI OK  ", service, startNanos));
            return result;
        } catch (RuntimeException e) {
            log.warn(rmiLine("RMI FAIL", service, startNanos) + "  " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error(rmiLine("RMI FAIL", service, startNanos), e);
            throw new RuntimeException("RMI communication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the aligned one-liner. Formatted with {@link String#format}
     * rather than SLF4J placeholders, which do not support width/alignment.
     */
    private String rmiLine(String status, String service, long startNanos) {
        return String.format("%-8s %-18s %6d ms  rmi://%s:%d",
                status, service, (System.nanoTime() - startNanos) / 1_000_000L, rmiHost, rmiPort);
    }

    @FunctionalInterface
    private interface RemoteCall<T> {
        T call() throws Exception;
    }
}