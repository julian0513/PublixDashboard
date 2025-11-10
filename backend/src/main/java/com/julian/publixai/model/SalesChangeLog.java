package com.julian.publixai.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SalesChangeLog
 *
 * Purpose: Append-only audit trail of changes to sales entries.
 *
 * Schema (see V3__sales_change_log.sql):
 * - id           uuid PK (DB default gen_random_uuid())
 * - sale_id      uuid NOT NULL  -> FK to sales(id), ON DELETE CASCADE
 * - product_name text NOT NULL
 * - date         date NOT NULL
 * - old_units    int  NOT NULL
 * - new_units    int  NOT NULL
 * - changed_at   timestamptz NOT NULL DEFAULT now()
 * - undone_at    timestamptz NULL
 *
 * Notes:
 * - Column names explicitly map to snake_case.
 * - changedAt is database-populated; marked insertable=false/updatable=false.
 * - Relation to SaleRecord is LAZY to avoid unnecessary loads in lists.
 */
@Entity
@Table(name = "sales_change_log", schema = "public")
public class SalesChangeLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private SaleRecord sale;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "old_units", nullable = false)
    private int oldUnits;

    @Column(name = "new_units", nullable = false)
    private int newUnits;

    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "undone_at")
    private Instant undoneAt;

    // ---- Constructors ----

    public SalesChangeLog() {
        // for JPA
    }

    public SalesChangeLog(SaleRecord sale, String productName, LocalDate date, int oldUnits, int newUnits) {
        this.sale = sale;
        this.productName = productName;
        this.date = date;
        this.oldUnits = oldUnits;
        this.newUnits = newUnits;
        // changedAt is DB-managed (DEFAULT now())
    }

    // ---- Getters / Setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SaleRecord getSale() { return sale; }
    public void setSale(SaleRecord sale) { this.sale = sale; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getOldUnits() { return oldUnits; }
    public void setOldUnits(int oldUnits) { this.oldUnits = oldUnits; }

    public int getNewUnits() { return newUnits; }
    public void setNewUnits(int newUnits) { this.newUnits = newUnits; }

    public Instant getChangedAt() { return changedAt; } // read-only (DB default)
    public Instant getUndoneAt() { return undoneAt; }
    public void setUndoneAt(Instant undoneAt) { this.undoneAt = undoneAt; }
}
