package com.atm.rmi.repository;

import com.atm.rmi.model.TxnRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TxnRecord, String> {
    List<TxnRecord> findTop20ByAccountNumberOrderByCreatedAtDesc(String accountNumber);
    List<TxnRecord> findByAccountNumberOrderByCreatedAtDesc(String accountNumber, Pageable pageable);
}
