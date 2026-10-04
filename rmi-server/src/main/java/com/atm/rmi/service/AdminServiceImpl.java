package com.atm.rmi.service;

import com.atm.rmi.common.Account;
import com.atm.rmi.common.AdminServiceRemote;
import com.atm.rmi.model.Customer;
import com.atm.rmi.model.TxnRecord;
import com.atm.rmi.repository.CustomerRepository;
import com.atm.rmi.repository.TransactionRepository;
import com.atm.rmi.exception.AccountNotFoundException;
import com.atm.rmi.exception.AuthenticationException;
import com.atm.rmi.exception.TransactionFailedException;
import com.atm.rmi.security.PinHasher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AdminServiceImpl implements AdminServiceRemote {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountServiceImpl accountService;

    private final ConcurrentHashMap<String, ReentrantLock> lockRegistry = new ConcurrentHashMap<>();

    private ReentrantLock acquireLock(String accountNumber) {
        return lockRegistry.computeIfAbsent(accountNumber, k -> new ReentrantLock());
    }

    private boolean isAdmin(String accountNumber) {
        Customer admin = customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AuthenticationException("Admin account not found"));
        return "ADMIN".equals(admin.getRole()) || "MANAGER".equals(admin.getRole());
    }

    private void requireAdmin(String adminAccountNumber) {
        if (!isAdmin(adminAccountNumber)) {
            throw new AuthenticationException("Insufficient privileges: ADMIN role required");
        }
    }

    @Override
    public List<Account> listPendingAccounts() throws RemoteException {
        List<Customer> pending = customerRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        List<Account> result = new ArrayList<>();
        for (Customer c : pending) {
            result.add(accountService.toAccount(c));
        }
        return result;
    }

    @Override
    public List<Account> listActiveAccounts() throws RemoteException {
        List<Customer> active = customerRepository.findByStatusAndRoleOrderByCreatedAtAsc("ACTIVE", "USER");
        List<Account> result = new ArrayList<>();
        for (Customer c : active) {
            result.add(accountService.toAccount(c));
        }
        return result;
    }

    @Override
    public Account approveAccount(String adminAccountNumber, String accountNumber) throws RemoteException {
        requireAdmin(adminAccountNumber);
        ReentrantLock lock = acquireLock(accountNumber);
        lock.lock();
        try {
            Customer customer = customerRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            if (!"PENDING".equals(customer.getStatus())) {
                throw new TransactionFailedException("Only pending accounts can be approved");
            }
            customer.setStatus("ACTIVE");
            customer.setActive(true);
            customerRepository.save(customer);
            return accountService.toAccount(customer);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Account rejectAccount(String adminAccountNumber, String accountNumber) throws RemoteException {
        requireAdmin(adminAccountNumber);
        ReentrantLock lock = acquireLock(accountNumber);
        lock.lock();
        try {
            Customer customer = customerRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            if (!"PENDING".equals(customer.getStatus())) {
                throw new TransactionFailedException("Only pending accounts can be rejected");
            }
            customer.setStatus("REJECTED");
            customer.setActive(false);
            customerRepository.save(customer);
            return accountService.toAccount(customer);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Account creditAccount(String adminAccountNumber, String accountNumber,
                                 BigDecimal amount, String reference) throws RemoteException {
        requireAdmin(adminAccountNumber);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionFailedException("Credit amount must be positive");
        }
        ReentrantLock lock = acquireLock(accountNumber);
        lock.lock();
        try {
            Customer customer = customerRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            if (!"ACTIVE".equals(customer.getStatus()) || !customer.isActive()) {
                throw new TransactionFailedException("Account must be approved before crediting funds");
            }

            BigDecimal newBalance = customer.getBalance().add(amount).setScale(4, RoundingMode.HALF_UP);
            customer.setBalance(newBalance);
            customerRepository.save(customer);

            TxnRecord txn = new TxnRecord();
            txn.setTransactionRef("CREDIT" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
            txn.setAccountNumber(accountNumber);
            txn.setType(TxnRecord.Type.DEPOSIT);
            txn.setAmount(amount);
            txn.setBalanceAfter(newBalance);
            txn.setReference(reference != null ? reference : "Bank admin credit");
            transactionRepository.save(txn);

            return accountService.toAccount(customer);
        } finally {
            lock.unlock();
        }
    }
}