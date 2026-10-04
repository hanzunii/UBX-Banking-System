package com.atm.rmi.service;

import com.atm.rmi.common.AuthServiceRemote;
import com.atm.rmi.model.Customer;
import com.atm.rmi.repository.CustomerRepository;
import com.atm.rmi.security.PinHasher;
import com.atm.rmi.security.JwtUtil;
import com.atm.rmi.exception.AccountNotFoundException;
import com.atm.rmi.exception.AccountLockedException;
import com.atm.rmi.exception.AuthenticationException;
import com.atm.rmi.config.AppConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthServiceRemote {

    // token -> accountNumber (in-memory session store for the RMI layer)
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    // accountNumber -> { otp, expiry } in-memory OTP store for MFA verification
    private static final class OtpEntry {
        final String otp;
        final LocalDateTime expiry;
        OtpEntry(String otp, LocalDateTime expiry) { this.otp = otp; this.expiry = expiry; }
    }
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AppConfig appConfig;

    public AuthServiceImpl() {
    }

    @Override
    public void preauthenticate(String accountNumber, String pin) throws RemoteException {
        validateCredentials(accountNumber, pin);
    }

    @Override
    public String login(String accountNumber, String pin, String deviceId) throws RemoteException {
        validateCredentials(accountNumber, pin);
        Customer customer = customerRepository.findByAccountNumber(accountNumber).orElseThrow();
        String token = jwtUtil.generateToken(customer.getAccountNumber(), customer.getRole());
        activeSessions.put(token, customer.getAccountNumber());
        return token;
    }

    private void validateCredentials(String accountNumber, String pin) {
        Customer customer = customerRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AuthenticationException("Invalid account number or PIN"));

        if (!"ACTIVE".equals(customer.getStatus())) {
            if ("PENDING".equals(customer.getStatus())) {
                throw new AuthenticationException("Account pending bank approval. Please wait.");
            }
            if ("REJECTED".equals(customer.getStatus())) {
                throw new AuthenticationException("Account registration was rejected.");
            }
            throw new AuthenticationException("Account is not active");
        }

        if (!customer.isActive()) {
            throw new AuthenticationException("Account is not active");
        }

        if (customer.getLockedUntil() != null && customer.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Account locked. Try again after " + customer.getLockedUntil());
        }

        boolean pinMatch = PinHasher.matches(pin, customer.getPinHash());
        if (!pinMatch) {
            customer.setFailedAttempts(customer.getFailedAttempts() + 1);
            if (customer.getFailedAttempts() >= appConfig.getMaxFailedAttempts()) {
                customer.setLockedUntil(LocalDateTime.now().plusMinutes(appConfig.getLockoutMinutes()));
                customer.setFailedAttempts(0);
            }
            customerRepository.save(customer);
            throw new AuthenticationException("Invalid account number or PIN");
        }

        // reset counters on success
        customer.setFailedAttempts(0);
        customer.setLockedUntil(null);
        customerRepository.save(customer);
    }

    @Override
    public String generateOtp(String accountNumber) throws RemoteException {
        if (!customerRepository.existsByAccountNumber(accountNumber)) {
            throw new AuthenticationException("Account not found");
        }
        String otp = String.format("%06d", OTP_RANDOM.nextInt(1000000));
        otpStore.put(accountNumber, new OtpEntry(otp, LocalDateTime.now().plusMinutes(5)));
        // In production the OTP would be dispatched via SMS/email. For the demo it is returned
        // so the client (Web/Android) can surface it in the MFA verification step.
        return otp;
    }

    @Override
    public boolean verifyOtp(String accountNumber, String otp) throws RemoteException {
        OtpEntry entry = otpStore.get(accountNumber);
        if (entry == null) {
            return false;
        }
        if (entry.expiry.isBefore(LocalDateTime.now())) {
            otpStore.remove(accountNumber);
            return false;
        }
        boolean ok = entry.otp.equals(otp);
        if (ok) {
            otpStore.remove(accountNumber);
        }
        return ok;
    }

    @Override
    public boolean register(String accountNumber, String holderName, String phone, String pin, String role) throws RemoteException {
        if (customerRepository.existsByAccountNumber(accountNumber)) {
            return false;
        }
        Customer customer = new Customer();
        customer.setAccountNumber(accountNumber);
        customer.setHolderName(holderName);
        customer.setPhone(phone);
        customer.setPinHash(PinHasher.hash(pin));
        customer.setRole("USER");
        customer.setBalance(java.math.BigDecimal.ZERO);
        // New registrations enter an approval queue; the bank admin must approve
        // the account before it can be used, and credit the opening deposit.
        customer.setStatus("PENDING");
        customer.setActive(false);
        customerRepository.save(customer);
        return true;
    }

    @Override
    public void logout(String token) throws RemoteException {
        activeSessions.remove(token);
    }

    public boolean isTokenValid(String token, String accountNumber) {
        return accountNumber.equals(activeSessions.get(token));
    }
}
