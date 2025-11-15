package com.julian.publixai.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.service.DiscountAnalysisService;
import com.julian.publixai.service.YearlyAnalysisService;
import com.julian.publixai.service.YearlyAnalysisService.YearlyProductAnalysis;

import jakarta.validation.constraints.NotBlank;

/**
 * YearlyAnalysisController
 * 
 * REST API for year-by-year product analysis (2015-2024).
 * Provides historical context for managers to understand trends and patterns.
 * 
 * Endpoints:
 * - GET /api/yearly/analysis?productName={name} - Get year-by-year breakdown
 */
@RestController
@RequestMapping("/api/yearly")
@Validated
public class YearlyAnalysisController {

    private final YearlyAnalysisService yearlyAnalysisService;
    private final DiscountRepository discountRepository;
    private final DiscountAnalysisService discountAnalysisService;

    public YearlyAnalysisController(
            YearlyAnalysisService yearlyAnalysisService,
            DiscountRepository discountRepository,
            DiscountAnalysisService discountAnalysisService) {
        this.yearlyAnalysisService = yearlyAnalysisService;
        this.discountRepository = discountRepository;
        this.discountAnalysisService = discountAnalysisService;
    }

    /**
     * GET /api/yearly/analysis?productName={name}
     * Get year-by-year analysis for a product (2015-2024).
     * 
     * Returns for each year:
     * - Top 10 frequently bought together items
     * - Active discounts in October of that year
     * - Discount effectiveness data
     * - Total units sold and revenue
     * 
     * @param productName Product name (supports fuzzy matching)
     * @return List of yearly analyses, ordered by year ascending
     */
    @GetMapping("/analysis")
    public List<YearlyProductAnalysis> getYearlyAnalysis(
            @RequestParam("productName") @NotBlank String productName) {
        // Check if discount data exists in database
        // If we have discount_effectiveness but no Discount records, generate them
        long totalDiscounts = discountRepository.count();
        long totalDiscountEffectiveness = discountAnalysisService.countAllDiscountEffectiveness();
        
        if (totalDiscountEffectiveness > 0 && totalDiscounts == 0) {
            try {
                discountAnalysisService.generateDiscountRecordsFromEffectiveness();
            } catch (Exception e) {
                // Silently fail - discount data generation is optional
            }
        }
        
        return yearlyAnalysisService.getYearlyAnalysis(productName);
    }
}

