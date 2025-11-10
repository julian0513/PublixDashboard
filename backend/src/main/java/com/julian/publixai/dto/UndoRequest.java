package com.julian.publixai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UndoRequest
 *
 * Purpose: Input payload to undo the most recent change for a product on a date.
 *
 * Fields:
 * - productName : required, non-blank product/SKU name
 * - date        : required, ISO-8601 (YYYY-MM-DD)
 *
 * Notes:
 * - Any further normalization (trim/collapse whitespace) is handled in the service layer.
 */
public class UndoRequest {

    @NotBlank
    private String productName;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    public UndoRequest() {
        // no-arg ctor for Jackson
    }

    public UndoRequest(String productName, LocalDate date) {
        this.productName = productName;
        this.date = date;
    }

    public String getProductName() { return productName; }
    public LocalDate getDate() { return date; }

    public void setProductName(String productName) { this.productName = productName; }
    public void setDate(LocalDate date) { this.date = date; }
}
