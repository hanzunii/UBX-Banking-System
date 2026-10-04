package com.atm.server.service;

import com.atm.server.dto.TransactionResponse;
import com.atm.server.model.Customer;
import com.atm.server.model.Transaction;
import com.atm.server.repository.CustomerRepository;
import com.atm.server.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BankingService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    public BankingService(CustomerRepository customerRepository, TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public double checkBalance(String accountNumber) {
        Customer c = requireActive(accountNumber);
        return c.getBalance();
    }

    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount) {
        Customer c = requireActive(accountNumber);
        double amt = amount.doubleValue();
        double newBalance = c.getBalance() + amt;
        c.setBalance(round(newBalance));
        customerRepository.save(c);

        Transaction tx = transactionRepository.save(
            new Transaction(c.getAccountNumber(), "CREDIT", amt, c.getBalance(), "CASH", "Deposit"));
        return toDto(tx);
    }

    @Transactional
    public TransactionResponse withdraw(String accountNumber, BigDecimal amount) {
        Customer c = requireActive(accountNumber);
        double amt = amount.doubleValue();
        double newBalance = c.getBalance() - amt;
        if (newBalance < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }
        c.setBalance(round(newBalance));
        customerRepository.save(c);

        Transaction tx = transactionRepository.save(
            new Transaction(c.getAccountNumber(), "DEBIT", amt, c.getBalance(), "CASH", "Withdrawal"));
        return toDto(tx);
    }

    @Transactional
    public String transfer(String fromAccount, BigDecimal amount, String toAccountNumber) {
        Customer from = requireActive(fromAccount);
        Customer to = customerRepository.findByAccountNumber(toAccountNumber)
            .orElseThrow(() -> new IllegalArgumentException("Recipient account not found."));
        if (!to.isActive()) {
            throw new IllegalArgumentException("Recipient account is not active.");
        }
        if (from.getAccountNumber().equals(to.getAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }

        double amt = amount.doubleValue();
        if (from.getBalance() - amt < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }

        from.setBalance(round(from.getBalance() - amt));
        to.setBalance(round(to.getBalance() + amt));
        customerRepository.save(from);
        customerRepository.save(to);

        String reference = "TXN-" + System.currentTimeMillis();
        transactionRepository.save(new Transaction(from.getAccountNumber(), "TRANSFER_OUT", amt, from.getBalance(),
            to.getAccountNumber(), reference));
        transactionRepository.save(new Transaction(to.getAccountNumber(), "TRANSFER_IN", amt, to.getBalance(),
            from.getAccountNumber(), reference));
        return reference;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> history(String accountNumber, int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        return transactionRepository
            .findByAccountNumberOrderByCreatedAtDesc(accountNumber, pageable)
            .stream()
            .map(this::toDto)
            .toList();
    }

    private Customer requireActive(String accountNumber) {
        Customer c = customerRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (!c.isActive()) {
            throw new IllegalArgumentException("Account is deactivated.");
        }
        return c;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private TransactionResponse toDto(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getType(), tx.getAmount(),
            tx.getBalanceAfter(), tx.getCounterparty(), tx.getReference(), tx.getCreatedAt());
    }
}