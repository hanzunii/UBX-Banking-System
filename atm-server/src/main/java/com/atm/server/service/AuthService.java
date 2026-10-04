package com.atm.server.service;

import com.atm.server.dto.AuthResponse;
import com.atm.server.dto.RegisterRequest;
import com.atm.server.model.Customer;
import com.atm.server.repository.CustomerRepository;
import com.atm.server.security.JwtUtil;
import com.atm.server.security.PinHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.rate-limit.window-minutes:15}")
    private int lockoutMinutes;

    public AuthService(CustomerRepository customerRepository, JwtUtil jwtUtil) {
        this.customerRepository = customerRepository;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public Customer register(RegisterRequest req) {
        if (customerRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("A user already registered with this phone.");
        }
        if (customerRepository.existsById(req.getAccountNumber())) {
            throw new IllegalArgumentException("Account number already exists.");
        }
        Customer customer = new Customer(
            req.getAccountNumber(),
            req.getPhone(),
            req.getHolderName().trim(),
            PinHasher.hash(req.getPin()),
            0.0
        );
        return customerRepository.save(customer);
    }

    @Transactional
    public AuthResponse login(String accountNumber, String pin) {
        Customer customer = customerRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new IllegalArgumentException("Invalid account number or PIN."));

        if (!customer.isActive()) {
            throw new IllegalArgumentException("Account is deactivated.");
        }
        if (customer.getLockedUntil() != null && customer.getLockedUntil().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Account temporarily locked. Try again later.");
        }

        if (!PinHasher.matches(pin, customer.getPinHash())) {
            handleFailedAttempt(customer);
            throw new IllegalArgumentException("Invalid account number or PIN.");
        }

        // success: reset counters
        customer.setFailedAttempts(0);
        customer.setLockedUntil(null);
        customerRepository.save(customer);

        String token = jwtUtil.generate(customer.getAccountNumber());
        return new AuthResponse(
            token,
            customer.getAccountNumber(),
            customer.getPhone(),
            customer.getHolderName(),
            customer.getBalance()
        );
    }

    private void handleFailedAttempt(Customer customer) {
        int attempts = customer.getFailedAttempts() + 1;
        customer.setFailedAttempts(attempts);
        if (attempts >= maxAttempts) {
            customer.setLockedUntil(Instant.now().plusSeconds(lockoutMinutes * 60L));
            customer.setFailedAttempts(0);
        }
        customerRepository.save(customer);
    }
}