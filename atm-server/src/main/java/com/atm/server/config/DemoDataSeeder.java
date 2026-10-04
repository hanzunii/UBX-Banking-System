package com.atm.server.config;

import com.atm.server.model.Customer;
import com.atm.server.repository.CustomerRepository;
import com.atm.server.repository.TransactionRepository;
import com.atm.server.security.PinHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    public DemoDataSeeder(CustomerRepository customerRepository, TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (customerRepository.count() > 0) {
            log.info("Demo data already present, skipping seed.");
            return;
        }

        Customer alice = new Customer("10000001", "09123456789", "Alice Maung",
            PinHasher.hash("1234"), 5000.00);
        Customer bob = new Customer("10000002", "09876543210", "Bob Ko",
            PinHasher.hash("5678"), 2500.00);
        customerRepository.save(alice);
        customerRepository.save(bob);

        log.info("Seeded demo accounts:" +
            " account=10000001 pin=1234 ($5000.00), account=10000002 pin=5678 ($2500.00)");
    }
}