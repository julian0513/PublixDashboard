package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Discount
 *
 * Purpose: Track discount promotions applied to products.
 *
 * Schema (see V7__create_discount_tracking.sql):
 * - id              uuid PK
 * - product_name    varchar(120) NOT NULL
 * - discount_percent decimal(5, 2) NOT NULL
 * - start_date      date NOT NULL
 * - end_date        date NOT NULL
 * - description     text
 * - created_at      timestamptz NOT NULL DEFAULT now()
 */
@Entity
@Table(name = "discounts")
public class Discount {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- Constructors ----

    public Discount() {
        // for JPA
    }

    public Discount(String productName, BigDecimal discountPercent, LocalDate startDate, LocalDate endDate, String description) {
        this.productName = productName;
        this.discountPercent = discountPercent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

