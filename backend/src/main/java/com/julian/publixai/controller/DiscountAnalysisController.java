package com.julian.publixai.controller;

import com.julian.publixai.dto.DiscountEffectivenessResponse;
import com.julian.publixai.model.Discount;
import com.julian.publixai.model.DiscountEffectiveness;
import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.service.DiscountAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DiscountAnalysis API
 * 
 * Endpoints for discount tracking and effectiveness analysis.
 * Automatically generates synthetic discount data (2015-2025) if none exists.
 * 
 * Note: Uses synthetic generation by default. The historical CSV only contains
 * basic sales data (units sold per day), not discount or basket analysis data.
 * Synthetic generation functions were built specifically to pair with this
 * historical sales data.
 */
@RestController
@RequestMapping("/api/discounts")
@Validated
public class DiscountAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(DiscountAnalysisController.class);

    private final DiscountAnalysisService discountAnalysisService;
    private final DiscountRepository discountRepository;
    private final com.julian.publixai.service.SampleDataService sampleDataService;

    public DiscountAnalysisController(
            DiscountAnalysisService discountAnalysisService,
            DiscountRepository discountRepository,
            com.julian.publixai.service.SampleDataService sampleDataService) {
        this.discountAnalysisService = discountAnalysisService;
        this.discountRepository = discountRepository;
        this.sampleDataService = sampleDataService;
    }

    /**
     * GET /api/discounts/active?date={date}
     * Get all active discounts on a specific date.
     */
    @GetMapping("/active")
    public List<Discount> getActiveDiscounts(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return discountAnalysisService.getActiveDiscounts(date);
    }

    /**
     * GET /api/discounts/active-for-product?productName={name}&date={date}
     * Get active discounts for a specific product.
     */
    @GetMapping("/active-for-product")
    public List<Discount> getActiveDiscountsForProduct(
            @RequestParam("productName") @NotBlank String productName,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return discountAnalysisService.getActiveDiscountsForProduct(productName, date);
    }

    /**
     * GET /api/discounts/effectiveness?productName={name}
     * Get discount effectiveness data for a product.
     * Automatically generates synthetic discount data (2015-2025) if none exists globally.
     * Note: Uses synthetic generation by default, as the historical CSV only contains basic sales data.
     */
    @GetMapping("/effectiveness")
    public DiscountEffectivenessResponse getDiscountEffectiveness(
            @RequestParam("productName") @NotBlank String productName,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {

        // Check if any discount effectiveness data exists globally
        long totalDiscountData = discountAnalysisService.countAllDiscountEffectiveness();
        long totalDiscounts = discountRepository.count();
        log.debug("Total discount effectiveness records: {}, Total Discount records: {}", totalDiscountData, totalDiscounts);

        // DISABLED: No longer auto-generating discount data to improve ML training performance
        // Discount data generation is too slow and causes ML training timeouts
        // Users can manually trigger discount data generation if needed via admin endpoints

        // Get discount effectiveness data for the product
        // This will:
        // 1. Try exact match from database
        // 2. Try partial/fuzzy matching from database
        // 3. For NEW products only: generate predicted optimal discounts using NewProductPredictionService
        // Note: We do NOT generate synthetic data for individual products - only use predictions for new products
        DiscountEffectivenessResponse response = discountAnalysisService.getDiscountEffectiveness(productName);
        log.debug("Found {} discount effectiveness records for product: {}, isPredicted: {}", 
                response.getItems().size(), productName, response.isPredicted());

        if (startDate != null && endDate != null) {
            List<DiscountEffectiveness> filtered = response.getItems().stream()
                    .filter(de -> !de.getDate().isBefore(startDate) && !de.getDate().isAfter(endDate))
                    .collect(java.util.stream.Collectors.toList());
            return new DiscountEffectivenessResponse(filtered, response.isPredicted(), response.getOptimalDiscountPercent());
        }

        return response;
    }

    /**
     * GET /api/discounts/optimal?productName={name}
     * Find the optimal discount percentage for a product.
     */
    @GetMapping("/optimal")
    public OptimalDiscountResponse getOptimalDiscount(
            @RequestParam("productName") @NotBlank String productName) {
        BigDecimal optimalDiscount = discountAnalysisService.findOptimalDiscount(productName);
        return new OptimalDiscountResponse(productName, optimalDiscount);
    }

    /**
     * POST /api/discounts
     * Create a new discount promotion.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Discount createDiscount(@Valid @RequestBody DiscountRequest request) {
        return discountAnalysisService.createDiscount(
                request.productName(),
                request.discountPercent(),
                request.startDate(),
                request.endDate(),
                request.description());
    }

    /**
     * POST /api/discounts/calculate-effectiveness
     * Calculate and record discount effectiveness.
     */
    @PostMapping("/calculate-effectiveness")
    @ResponseStatus(HttpStatus.OK)
    public void calculateDiscountEffectiveness(@Valid @RequestBody DiscountEffectivenessRequest request) {
        discountAnalysisService.calculateDiscountEffectiveness(
                request.productName(),
                request.date(),
                request.discountPercent(),
                request.unitsSold(),
                request.avgUnitPrice());
    }

    // DTOs
    public record DiscountRequest(
            @NotBlank String productName,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPercent,
            @NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
            String description) {
    }

    public record DiscountEffectivenessRequest(
            @NotBlank String productName,
            @NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent,
            @NotNull int unitsSold,
            @NotNull @DecimalMin("0.00") BigDecimal avgUnitPrice) {
    }

    public record OptimalDiscountResponse(
            String productName,
            BigDecimal optimalDiscountPercent) {
    }
}
