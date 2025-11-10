package com.julian.publixai.controller;

import com.julian.publixai.model.SaleRecord;
import com.julian.publixai.service.SalesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sales API
 * - Explicit @RequestParam/@PathVariable names avoid the -parameters compiler flag issue.
 * - Returns 200 with [] for any date (including non-October).
 * - Returns 204 on successful delete; ignores missing id to stay UX-friendly.
 */
@RestController
@RequestMapping("/api")
@Validated
public class SalesController {

    private final SalesService sales;

    public SalesController(SalesService sales) {
        this.sales = sales;
    }

    @GetMapping("/sales")
    public List<SaleRecord> listByDate(
            @RequestParam("date") @DateTimeFormat(iso = ISO.DATE) LocalDate date
    ) {
        return sales.listByDate(date);
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleRecord create(@Valid @RequestBody SaleRequest body) {
        return sales.create(body.productName(), body.units(), body.date());
    }

    @DeleteMapping("/sales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        sales.delete(id);
    }

    /** Request body DTO for POST /api/sales */
    public record SaleRequest(
            @NotBlank String productName,
            @Min(1) int units,
            @NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate date
    ) {}
}
