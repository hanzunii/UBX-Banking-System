package com.atm.rmi.service;

import com.atm.rmi.common.Transaction;
import com.atm.rmi.common.TransactionServiceRemote;
import com.atm.rmi.model.Customer;
import com.atm.rmi.model.TxnRecord;
import com.atm.rmi.repository.CustomerRepository;
import com.atm.rmi.repository.TransactionRepository;
import com.atm.rmi.exception.InsufficientBalanceException;
import com.atm.rmi.exception.TransactionFailedException;
import com.atm.rmi.exception.AccountNotFoundException;
import com.atm.rmi.security.PinHasher;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TransactionServiceImpl implements TransactionServiceRemote {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<TransactionServiceImpl> selfProvider;

    private TransactionServiceImpl self() {
        return selfProvider.getObject();
    }

    private final ConcurrentHashMap<String, ReentrantLock> lockRegistry = new ConcurrentHashMap<>();

    private ReentrantLock acquireLock(String accountNumber) {
        return lockRegistry.computeIfAbsent(accountNumber, k -> new ReentrantLock());
    }

    public TransactionServiceImpl() {
    }

    @Override
    public Transaction transfer(String senderAccountNumber, String receiverAccountNumber,
                                BigDecimal amount, String reference) throws RemoteException {
        if (senderAccountNumber.equals(receiverAccountNumber)) {
            throw new TransactionFailedException("Cannot transfer to the same account");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionFailedException("Transfer amount must be positive");
        }

        String first  = senderAccountNumber.compareTo(receiverAccountNumber) <= 0 ? senderAccountNumber : receiverAccountNumber;
        String second = senderAccountNumber.compareTo(receiverAccountNumber) <= 0 ? receiverAccountNumber : senderAccountNumber;
        ReentrantLock lock1 = acquireLock(first);
        ReentrantLock lock2 = acquireLock(second);
        lock1.lock();
        try {
            lock2.lock();
            try {
                return self().doTransfer(senderAccountNumber, receiverAccountNumber, amount, reference);
            } finally {
                lock2.unlock();
            }
        } finally {
            lock1.unlock();
        }
    }

    @Override
    public Transaction deposit(String accountNumber, BigDecimal amount) throws RemoteException {
        ReentrantLock lock = acquireLock(accountNumber);
        lock.lock();
        try {
            return self().doDeposit(accountNumber, amount);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Transaction withdraw(String accountNumber, BigDecimal amount, String pin) throws RemoteException {
        ReentrantLock lock = acquireLock(accountNumber);
        lock.lock();
        try {
            return self().doWithdraw(accountNumber, amount, pin);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    Transaction doTransfer(String senderAccountNumber, String receiverAccountNumber,
                            BigDecimal amount, String reference) {
        Customer freshSender = customerRepository.findByAccountNumber(senderAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found"));
        Customer freshReceiver = customerRepository.findByAccountNumber(receiverAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Receiver account not found"));

        if (!freshSender.isActive()) {
            throw new TransactionFailedException("Sender account is deactivated");
        }
        if (!freshReceiver.isActive()) {
            throw new TransactionFailedException("Receiver account is deactivated");
        }
        if (freshSender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in sender account");
        }

        BigDecimal senderNewBalance = freshSender.getBalance().subtract(amount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal receiverNewBalance = freshReceiver.getBalance().add(amount).setScale(4, RoundingMode.HALF_UP);

        freshSender.setBalance(senderNewBalance);
        freshReceiver.setBalance(receiverNewBalance);

        String txnRef = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

        TxnRecord senderTxn = new TxnRecord();
        senderTxn.setTransactionRef(txnRef);
        senderTxn.setAccountNumber(senderAccountNumber);
        senderTxn.setCounterpartyAccount(receiverAccountNumber);
        senderTxn.setType(TxnRecord.Type.TRANSFER);
        senderTxn.setAmount(amount.negate());
        senderTxn.setBalanceAfter(senderNewBalance);
        senderTxn.setReference(reference);
        transactionRepository.save(senderTxn);

        TxnRecord receiverTxn = new TxnRecord();
        receiverTxn.setTransactionRef(txnRef + "-R");
        receiverTxn.setAccountNumber(receiverAccountNumber);
        receiverTxn.setCounterpartyAccount(senderAccountNumber);
        receiverTxn.setType(TxnRecord.Type.TRANSFER);
        receiverTxn.setAmount(amount);
        receiverTxn.setBalanceAfter(receiverNewBalance);
        receiverTxn.setReference(reference);
        transactionRepository.save(receiverTxn);

        customerRepository.save(freshSender);
        customerRepository.save(freshReceiver);

        Transaction txn = new Transaction();
        txn.setTransactionRef(txnRef);
        txn.setFromAccountNumber(senderAccountNumber);
        txn.setToAccountNumber(receiverAccountNumber);
        txn.setAmount(amount);
        txn.setType(Transaction.Type.TRANSFER);
        txn.setReference(reference);
        txn.setSenderBalanceAfter(senderNewBalance);
        txn.setReceiverBalanceAfter(receiverNewBalance);
        txn.setTimestamp(java.time.LocalDateTime.now());
        return txn;
    }

    @Transactional
    Transaction doDeposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionFailedException("Deposit amount must be positive");
        }
        Customer customer = customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if (!customer.isActive()) {
            throw new TransactionFailedException("Account is deactivated");
        }

        BigDecimal newBalance = customer.getBalance().add(amount).setScale(4, RoundingMode.HALF_UP);
        customer.setBalance(newBalance);
        customerRepository.save(customer);

        TxnRecord txn = new TxnRecord();
        txn.setTransactionRef("TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        txn.setAccountNumber(accountNumber);
        txn.setType(TxnRecord.Type.DEPOSIT);
        txn.setAmount(amount);
        txn.setBalanceAfter(newBalance);
        transactionRepository.save(txn);

        Transaction result = new Transaction();
        result.setTransactionRef(txn.getTransactionRef());
        result.setFromAccountNumber(accountNumber);
        result.setAmount(amount);
        result.setType(Transaction.Type.DEPOSIT);
        result.setSenderBalanceAfter(newBalance);
        result.setTimestamp(txn.getCreatedAt());
        return result;
    }

    @Transactional
    Transaction doWithdraw(String accountNumber, BigDecimal amount, String pin) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionFailedException("Withdrawal amount must be positive");
        }
        Customer customer = customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if (!customer.isActive()) {
            throw new TransactionFailedException("Account is deactivated");
        }
        if (!PinHasher.matches(pin, customer.getPinHash())) {
            throw new TransactionFailedException("Invalid PIN");
        }
        if (customer.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        BigDecimal newBalance = customer.getBalance().subtract(amount).setScale(4, RoundingMode.HALF_UP);
        customer.setBalance(newBalance);
        customerRepository.save(customer);

        TxnRecord txn = new TxnRecord();
        txn.setTransactionRef("TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        txn.setAccountNumber(accountNumber);
        txn.setType(TxnRecord.Type.WITHDRAWAL);
        txn.setAmount(amount.negate());
        txn.setBalanceAfter(newBalance);
        transactionRepository.save(txn);

        Transaction result = new Transaction();
        result.setTransactionRef(txn.getTransactionRef());
        result.setFromAccountNumber(accountNumber);
        result.setAmount(amount);
        result.setType(Transaction.Type.WITHDRAWAL);
        result.setSenderBalanceAfter(newBalance);
        result.setTimestamp(txn.getCreatedAt());
        return result;
    }

    @Override
    public List<Transaction> getHistory(String accountNumber, int limit) throws RemoteException {
        if (limit <= 0) limit = 20;
        if (limit > 200) limit = 200;
        List<TxnRecord> records = transactionRepository
                .findByAccountNumberOrderByCreatedAtDesc(accountNumber,
                        org.springframework.data.domain.PageRequest.of(0, limit));

        List<Transaction> result = new ArrayList<>();
        for (TxnRecord r : records) {
            Transaction t = new Transaction();
            t.setId(r.getId());
            t.setTransactionRef(r.getTransactionRef());
            t.setFromAccountNumber(r.getAccountNumber());
            t.setToAccountNumber(r.getCounterpartyAccount());
            t.setAmount(r.getAmount().abs());
            t.setType(Transaction.Type.valueOf(r.getType().name()));
            t.setReference(r.getReference());
            t.setSenderBalanceAfter(r.getBalanceAfter());
            t.setTimestamp(r.getCreatedAt());
            if (r.getType() == TxnRecord.Type.TRANSFER) {
                t.setFromAccountNumber(r.getAmount().signum() < 0 ? r.getAccountNumber() : r.getCounterpartyAccount());
                t.setToAccountNumber(r.getAmount().signum() < 0 ? r.getCounterpartyAccount() : r.getAccountNumber());
            }
            result.add(t);
        }
        return result;
    }
}
