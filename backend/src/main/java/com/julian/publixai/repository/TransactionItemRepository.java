package com.julian.publixai.repository;

import com.julian.publixai.model.TransactionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionItemRepository extends JpaRepository<TransactionItem, UUID> {

    List<TransactionItem> findByProductName(String productName);

    @Query("SELECT ti FROM TransactionItem ti WHERE ti.transaction.transactionDate = :date")
    List<TransactionItem> findByTransactionDate(@Param("date") LocalDate date);

    @Query("SELECT ti FROM TransactionItem ti WHERE ti.transaction.transactionDate BETWEEN :start AND :end")
    List<TransactionItem> findByTransactionDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}

