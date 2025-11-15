package com.julian.publixai.repository;

import com.julian.publixai.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByTransactionDate(LocalDate date);

    List<Transaction> findByTransactionDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate = :date ORDER BY t.transactionTime")
    List<Transaction> findByDateOrderByTime(@Param("date") LocalDate date);
}

