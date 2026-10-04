package com.atm.server.repository;

import com.atm.server.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByAccountNumber(String accountNumber);

    Optional<Customer> findByPhone(String phone);

    boolean existsByPhone(String phone);
}