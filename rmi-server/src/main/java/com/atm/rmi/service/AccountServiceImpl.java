package com.atm.rmi.service;

import com.atm.rmi.common.Account;
import com.atm.rmi.common.AccountServiceRemote;
import com.atm.rmi.model.Customer;
import com.atm.rmi.repository.CustomerRepository;
import com.atm.rmi.exception.AccountNotFoundException;
import com.atm.rmi.exception.AuthenticationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;

@Service
public class AccountServiceImpl implements AccountServiceRemote {

    @Autowired
    private CustomerRepository customerRepository;

    public AccountServiceImpl() {
    }

    @Override
    public Account getAccountDetails(String accountNumber) throws RemoteException {
        Customer customer = customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
        return toAccount(customer);
    }

    @Override
    public Account getAccountById(String accountNumber, Long id) throws RemoteException {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        return toAccount(customer);
    }

    public Account toAccount(Customer customer) {
        Account account = new Account();
        account.setId(customer.getId());
        account.setAccountNumber(customer.getAccountNumber());
        account.setHolderName(customer.getHolderName());
        account.setBalance(customer.getBalance());
        account.setCurrency(customer.getCurrency());
        account.setRole(customer.getRole());
        account.setActive(customer.isActive());
        account.setStatus(customer.getStatus());
        account.setCreatedAt(customer.getCreatedAt());
        return account;
    }
}
