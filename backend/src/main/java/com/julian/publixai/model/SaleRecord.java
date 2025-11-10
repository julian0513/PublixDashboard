package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SaleRecord
 *
 * Purpose: JPA entity for the "sales" table (daily units per product).
 *
 * Schema (see V1__create_sales.sql):
 * - id            uuid PK (DB default gen_random_uuid())
 * - product_name  varchar(120) not null
 * - units         int not null (>= 0)
 * - date          date not null
 * - created_at    timestamptz not null default now()
 *
 * Notes:
 * - Column names are mapped explicitly to snake_case.
 * - id is generated in-app (UuidGenerator); DB default is a safe fallback.
 * - createdAt is database-populated; marked insertable=false/updatable=false.
 */
@Entity
@Table(name = "sales")
public class SaleRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "units", nullable = false)
    private int units;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // DB-managed timestamp with time zone (timestamptz)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- Constructors ----

    public SaleRecord() {
        // for JPA
    }

    public SaleRecord(String productName, int units, LocalDate date) {
        this.productName = productName;
        this.units = units;
        this.date = date;
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
