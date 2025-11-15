package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * TransactionItem
 *
 * Purpose: Individual items within a transaction.
 *
 * Schema (see V6__create_transactions_and_basket_analysis.sql):
 * - id              uuid PK
 * - transaction_id uuid NOT NULL FK -> transactions(id)
 * - product_name    varchar(120) NOT NULL
 * - quantity        int NOT NULL CHECK (quantity > 0)
 * - unit_price      decimal(10, 2) NOT NULL
 * - discount_percent decimal(5, 2) DEFAULT 0
 * - created_at      timestamptz NOT NULL DEFAULT now()
 */
@Entity
@Table(name = "transaction_items")
public class TransactionItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- Constructors ----

    public TransactionItem() {
        // for JPA
    }

    public TransactionItem(Transaction transaction, String productName, int quantity, BigDecimal unitPrice, BigDecimal discountPercent) {
        this.transaction = transaction;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountPercent = discountPercent != null ? discountPercent : BigDecimal.ZERO;
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    // Helper method to calculate final price
    public BigDecimal getFinalPrice() {
        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
            discountPercent.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP)
        );
        return unitPrice.multiply(discountMultiplier).multiply(BigDecimal.valueOf(quantity));
    }
}

