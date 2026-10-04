package com.atm.rmi.config;

import com.atm.rmi.model.Customer;
import com.atm.rmi.repository.CustomerRepository;
import com.atm.rmi.security.PinHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DemoDataSeeder implements CommandLineRunner {

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    public void run(String... args) {
        seedIfMissing("10000001", "Alice Johnson", "+12345678901", "1234", "USER", "5000.00", "USD");
        seedIfMissing("10000002", "Bob Smith", "+12345678902", "5678", "USER", "2500.00", "USD");
        seedIfMissing("10000003", "Carol White", "+12345678903", "1122", "MANAGER", "10000.00", "USD");
    }

    private void seedIfMissing(String accountNumber, String name, String phone,
                               String pin, String role, String balance, String currency) {
        if (!customerRepository.existsByAccountNumber(accountNumber)) {
            Customer customer = new Customer();
            customer.setAccountNumber(accountNumber);
            customer.setHolderName(name);
            customer.setPhone(phone);
            customer.setPinHash(PinHasher.hash(pin));
            customer.setRole(role);
            customer.setBalance(new BigDecimal(balance));
            customer.setCurrency(currency);
            customerRepository.save(customer);
            System.out.println("Seeded demo account: " + accountNumber + " (" + name + ")");
        }
    }
}
