package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transaction
 *
 * Purpose: Represents a single purchase/transaction containing multiple items.
 *
 * Schema (see V6__create_transactions_and_basket_analysis.sql):
 * - id              uuid PK
 * - transaction_date date NOT NULL
 * - transaction_time time NOT NULL
 * - created_at      timestamptz NOT NULL DEFAULT now()
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "transaction_time", nullable = false)
    private LocalTime transactionTime;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionItem> items = new ArrayList<>();

    // ---- Constructors ----

    public Transaction() {
        // for JPA
    }

    public Transaction(LocalDate transactionDate, LocalTime transactionTime) {
        this.transactionDate = transactionDate;
        this.transactionTime = transactionTime;
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public LocalTime getTransactionTime() { return transactionTime; }
    public void setTransactionTime(LocalTime transactionTime) { this.transactionTime = transactionTime; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public List<TransactionItem> getItems() { return items; }
    public void setItems(List<TransactionItem> items) { this.items = items; }
}

