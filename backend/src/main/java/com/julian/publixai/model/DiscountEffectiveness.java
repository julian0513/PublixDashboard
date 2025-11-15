package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DiscountEffectiveness
 *
 * Purpose: Track sales performance with/without discounts.
 *
 * Schema (see V7__create_discount_tracking.sql):
 * - id                  uuid PK
 * - product_name        varchar(120) NOT NULL
 * - discount_percent    decimal(5, 2) NOT NULL
 * - date                date NOT NULL
 * - units_sold          int NOT NULL DEFAULT 0
 * - revenue             decimal(12, 2) NOT NULL DEFAULT 0
 * - avg_unit_price      decimal(10, 2) NOT NULL
 * - sales_lift_percent  decimal(8, 2)
 * - created_at          timestamptz NOT NULL DEFAULT now()
 */
@Entity
@Table(name = "discount_effectiveness")
public class DiscountEffectiveness {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "units_sold", nullable = false)
    private int unitsSold;

    @Column(name = "revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue;

    @Column(name = "avg_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgUnitPrice;

    @Column(name = "sales_lift_percent", precision = 8, scale = 2)
    private BigDecimal salesLiftPercent;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- Constructors ----

    public DiscountEffectiveness() {
        // for JPA
    }

    public DiscountEffectiveness(String productName, BigDecimal discountPercent, LocalDate date,
                                 int unitsSold, BigDecimal revenue, BigDecimal avgUnitPrice, BigDecimal salesLiftPercent) {
        this.productName = productName;
        this.discountPercent = discountPercent;
        this.date = date;
        this.unitsSold = unitsSold;
        this.revenue = revenue;
        this.avgUnitPrice = avgUnitPrice;
        this.salesLiftPercent = salesLiftPercent;
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getUnitsSold() { return unitsSold; }
    public void setUnitsSold(int unitsSold) { this.unitsSold = unitsSold; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getAvgUnitPrice() { return avgUnitPrice; }
    public void setAvgUnitPrice(BigDecimal avgUnitPrice) { this.avgUnitPrice = avgUnitPrice; }

    public BigDecimal getSalesLiftPercent() { return salesLiftPercent; }
    public void setSalesLiftPercent(BigDecimal salesLiftPercent) { this.salesLiftPercent = salesLiftPercent; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

