package com.julian.publixai.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.julian.publixai.service.HalloweenReadinessService;
import com.julian.publixai.service.HalloweenReadinessService.HalloweenReadinessScore;

import jakarta.validation.constraints.NotBlank;

/**
 * HalloweenReadinessController
 * 
 * REST API for Halloween Readiness Score calculation.
 * Provides composite scores (0-100) to help managers make stocking decisions.
 * 
 * Endpoints:
 * - GET /api/halloween/readiness?productName={name} - Get readiness score for a product
 */
@RestController
@RequestMapping("/api/halloween")
@Validated
public class HalloweenReadinessController {

    private final HalloweenReadinessService halloweenReadinessService;

    public HalloweenReadinessController(HalloweenReadinessService halloweenReadinessService) {
        this.halloweenReadinessService = halloweenReadinessService;
    }

    /**
     * GET /api/halloween/readiness?productName={name}
     * Get Halloween Readiness Score for a product.
     * 
     * Returns a composite score (0-100) with breakdown:
     * - Trend Score (0-25): Historical performance trend
     * - Discount Score (0-25): Discount effectiveness
     * - Basket Score (0-25): Cross-sell potential
     * - Demand Score (0-25): Predicted demand
     * - Recommendation: Actionable advice
     * 
     * @param productName Product name (supports fuzzy matching)
     * @return HalloweenReadinessScore with composite score and breakdown
     */
    @GetMapping("/readiness")
    public HalloweenReadinessScore getReadinessScore(
            @RequestParam("productName") @NotBlank String productName) {
        return halloweenReadinessService.calculateReadinessScore(productName);
    }
}

