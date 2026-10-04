package com.atm.rmi.repository;

import com.atm.rmi.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByAccountNumber(String accountNumber);
    boolean existsByAccountNumber(String accountNumber);
    List<Customer> findByStatusOrderByCreatedAtAsc(String status);
    List<Customer> findByStatusAndRoleOrderByCreatedAtAsc(String status, String role);
}
