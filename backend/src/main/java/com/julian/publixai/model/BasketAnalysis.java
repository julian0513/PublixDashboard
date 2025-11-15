package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * BasketAnalysis
 *
 * Purpose: Pre-computed frequently bought together relationships.
 *
 * Schema (see V6__create_transactions_and_basket_analysis.sql):
 * - id                  uuid PK
 * - primary_product     varchar(120) NOT NULL
 * - associated_product  varchar(120) NOT NULL
 * - co_occurrence_count int NOT NULL DEFAULT 0
 * - confidence_score    decimal(5, 4) NOT NULL
 * - support_score       decimal(5, 4) NOT NULL
 * - last_calculated_at  timestamptz NOT NULL DEFAULT now()
 * - created_at          timestamptz NOT NULL DEFAULT now()
 */
@Entity
@Table(name = "basket_analysis")
public class BasketAnalysis {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "primary_product", nullable = false, length = 120)
    private String primaryProduct;

    @Column(name = "associated_product", nullable = false, length = 120)
    private String associatedProduct;

    @Column(name = "co_occurrence_count", nullable = false)
    private int coOccurrenceCount;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "support_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal supportScore;

    @Column(name = "last_calculated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime lastCalculatedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- Constructors ----

    public BasketAnalysis() {
        // for JPA
    }

    public BasketAnalysis(String primaryProduct, String associatedProduct, int coOccurrenceCount, 
                         BigDecimal confidenceScore, BigDecimal supportScore) {
        this.primaryProduct = primaryProduct;
        this.associatedProduct = associatedProduct;
        this.coOccurrenceCount = coOccurrenceCount;
        this.confidenceScore = confidenceScore;
        this.supportScore = supportScore;
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPrimaryProduct() { return primaryProduct; }
    public void setPrimaryProduct(String primaryProduct) { this.primaryProduct = primaryProduct; }

    public String getAssociatedProduct() { return associatedProduct; }
    public void setAssociatedProduct(String associatedProduct) { this.associatedProduct = associatedProduct; }

    public int getCoOccurrenceCount() { return coOccurrenceCount; }
    public void setCoOccurrenceCount(int coOccurrenceCount) { this.coOccurrenceCount = coOccurrenceCount; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public BigDecimal getSupportScore() { return supportScore; }
    public void setSupportScore(BigDecimal supportScore) { this.supportScore = supportScore; }

    public OffsetDateTime getLastCalculatedAt() { return lastCalculatedAt; }
    public void setLastCalculatedAt(OffsetDateTime lastCalculatedAt) { this.lastCalculatedAt = lastCalculatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

