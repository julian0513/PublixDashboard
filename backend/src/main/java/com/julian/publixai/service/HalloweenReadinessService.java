package com.julian.publixai.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.julian.publixai.dto.BasketAnalysisDTO;
import com.julian.publixai.dto.BasketAnalysisResponse;
import com.julian.publixai.model.DiscountEffectiveness;
import com.julian.publixai.model.SaleRecord;
import com.julian.publixai.repository.DiscountEffectivenessRepository;
import com.julian.publixai.repository.SaleRepository;

/**
 * HalloweenReadinessService
 * 
 * Purpose: Calculate a composite "Halloween Readiness Score" (0-100) for products.
 * 
 * Score factors:
 * - Historical performance trend (trending up/down)
 * - Discount effectiveness (how well discounts drive sales)
 * - Cross-sell potential (basket analysis associations)
 * - Stock levels vs predicted demand (if available)
 * 
 * This score helps managers quickly identify which products are
 * best positioned for Halloween sales success.
 */
@Service
public class HalloweenReadinessService {

    private final SaleRepository saleRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final BasketAnalysisService basketAnalysisService;

    public HalloweenReadinessService(
            SaleRepository saleRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            BasketAnalysisService basketAnalysisService) {
        this.saleRepository = saleRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.basketAnalysisService = basketAnalysisService;
    }

    /**
     * HalloweenReadinessScore DTO
     * Contains the composite score and breakdown of factors.
     */
    public static class HalloweenReadinessScore {
        private final String productName;
        private final int overallScore; // 0-100
        private final int trendScore; // 0-25 (historical performance trend)
        private final int discountScore; // 0-25 (discount effectiveness)
        private final int basketScore; // 0-25 (cross-sell potential)
        private final int demandScore; // 0-25 (predicted demand vs stock, simplified)
        private final String recommendation; // Actionable recommendation

        public HalloweenReadinessScore(String productName, int overallScore, int trendScore, int discountScore,
                int basketScore, int demandScore, String recommendation) {
            this.productName = productName;
            this.overallScore = overallScore;
            this.trendScore = trendScore;
            this.discountScore = discountScore;
            this.basketScore = basketScore;
            this.demandScore = demandScore;
            this.recommendation = recommendation;
        }

        public String getProductName() {
            return productName;
        }

        public int getOverallScore() {
            return overallScore;
        }

        public int getTrendScore() {
            return trendScore;
        }

        public int getDiscountScore() {
            return discountScore;
        }

        public int getBasketScore() {
            return basketScore;
        }

        public int getDemandScore() {
            return demandScore;
        }

        public String getRecommendation() {
            return recommendation;
        }
    }

    /**
     * Calculate Halloween Readiness Score for a product.
     * 
     * @param productName Product name (supports fuzzy matching)
     * @return HalloweenReadinessScore with composite score and breakdown
     */
    @Transactional(readOnly = true)
    public HalloweenReadinessScore calculateReadinessScore(String productName) {
        // 1. Trend Score (0-25): Historical performance trend
        int trendScore = calculateTrendScore(productName);

        // 2. Discount Score (0-25): Discount effectiveness
        int discountScore = calculateDiscountScore(productName);

        // 3. Basket Score (0-25): Cross-sell potential
        int basketScore = calculateBasketScore(productName);

        // 4. Demand Score (0-25): Predicted demand (simplified - using historical average)
        int demandScore = calculateDemandScore(productName);

        // Overall score (sum of all factors)
        int overallScore = trendScore + discountScore + basketScore + demandScore;

        // Generate recommendation
        String recommendation = generateRecommendation(overallScore, trendScore, discountScore, basketScore, demandScore);

        return new HalloweenReadinessScore(productName, overallScore, trendScore, discountScore, basketScore,
                demandScore, recommendation);
    }

    /**
     * Calculate trend score based on historical performance (2015-2024).
     * Compares first half vs second half of the 10-year period.
     */
    private int calculateTrendScore(String productName) {
        // Get sales data for each year (2015-2024)
        int[] yearlyUnits = new int[10];
        for (int year = 2015; year <= 2024; year++) {
            LocalDate yearStart = LocalDate.of(year, 10, 1);
            LocalDate yearEnd = LocalDate.of(year, 10, 31);
            List<SaleRecord> sales = saleRepository.findByDateBetween(yearStart, yearEnd);
            yearlyUnits[year - 2015] = sales.stream()
                    .filter(s -> matchesProductName(s.getProductName(), productName))
                    .mapToInt(SaleRecord::getUnits)
                    .sum();
        }

        // Calculate first half average (2015-2019) vs second half average (2020-2024)
        double firstHalfAvg = 0;
        double secondHalfAvg = 0;
        for (int i = 0; i < 5; i++) {
            firstHalfAvg += yearlyUnits[i];
        }
        firstHalfAvg /= 5.0;
        for (int i = 5; i < 10; i++) {
            secondHalfAvg += yearlyUnits[i];
        }
        secondHalfAvg /= 5.0;

        // Calculate growth rate
        if (firstHalfAvg == 0) {
            return 12; // Neutral score if no historical data
        }
        double growthRate = ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100;

        // Score: +15% growth = 25 points, 0% = 12 points, -15% = 0 points
        int score = (int) Math.round(12 + (growthRate / 15.0) * 13);
        return Math.max(0, Math.min(25, score)); // Clamp to 0-25
    }

    /**
     * Calculate discount effectiveness score.
     * Based on average sales lift from discounts.
     */
    private int calculateDiscountScore(String productName) {
        // Use optimized query instead of findAll()
        List<DiscountEffectiveness> effectiveness = discountEffectivenessRepository.findByProductName(productName);
        
        // If no exact match, try fuzzy matching with all records (fallback)
        if (effectiveness.isEmpty()) {
            effectiveness = discountEffectivenessRepository.findAll().stream()
                    .filter(de -> matchesProductName(de.getProductName(), productName))
                    .collect(Collectors.toList());
        }

        if (effectiveness.isEmpty()) {
            return 12; // Neutral score if no discount data
        }

        // Calculate average sales lift
        double avgLift = effectiveness.stream()
                .mapToDouble(de -> de.getSalesLiftPercent().doubleValue())
                .average()
                .orElse(0.0);

        // Score: 30%+ lift = 25 points, 0% = 12 points, negative = 0 points
        int score = (int) Math.round(12 + (avgLift / 30.0) * 13);
        return Math.max(0, Math.min(25, score)); // Clamp to 0-25
    }

    /**
     * Calculate basket analysis score (cross-sell potential).
     * Based on number of associations and confidence scores.
     */
    private int calculateBasketScore(String productName) {
        BasketAnalysisResponse response = basketAnalysisService.getFrequentlyBoughtTogether(productName);
        List<BasketAnalysisDTO> associations = response.getItems();

        if (associations.isEmpty()) {
            return 8; // Lower score if no associations
        }

        // Score based on number of associations and average confidence
        int numAssociations = associations.size();
        double avgConfidence = associations.stream()
                .mapToDouble(ba -> ba.getConfidenceScore().doubleValue())
                .average()
                .orElse(0.0);

        // Score: 10+ associations with 0.3+ avg confidence = 25 points
        int associationScore = Math.min(15, numAssociations * 2); // Max 15 for 7+ associations
        int confidenceScore = (int) Math.round(avgConfidence * 10); // Max 10 for 1.0 confidence
        return Math.min(25, associationScore + confidenceScore);
    }

    /**
     * Calculate demand score (simplified).
     * Uses historical average as proxy for predicted demand.
     */
    private int calculateDemandScore(String productName) {
        // Get average units sold in October across all years
        double totalUnits = 0;
        int yearCount = 0;
        for (int year = 2015; year <= 2024; year++) {
            LocalDate yearStart = LocalDate.of(year, 10, 1);
            LocalDate yearEnd = LocalDate.of(year, 10, 31);
            List<SaleRecord> sales = saleRepository.findByDateBetween(yearStart, yearEnd);
            int units = sales.stream()
                    .filter(s -> matchesProductName(s.getProductName(), productName))
                    .mapToInt(SaleRecord::getUnits)
                    .sum();
            if (units > 0) {
                totalUnits += units;
                yearCount++;
            }
        }

        if (yearCount == 0) {
            return 12; // Neutral score if no data
        }

        double avgUnits = totalUnits / yearCount;

        // Score: 100+ avg units = 25 points, 50 = 12 points, 0 = 0 points
        int score = (int) Math.round((avgUnits / 100.0) * 25);
        return Math.max(0, Math.min(25, score)); // Clamp to 0-25
    }

    /**
     * Generate actionable recommendation based on scores.
     */
    private String generateRecommendation(int overallScore, int trendScore, int discountScore, int basketScore,
            int demandScore) {
        if (overallScore >= 85) {
            return "High confidence - Increase stock and consider premium placement. Strong historical performance and discount effectiveness.";
        } else if (overallScore >= 70) {
            return "Good position - Maintain current stock levels. Monitor trends and consider targeted discounts.";
        } else if (overallScore >= 55) {
            return "Moderate readiness - Consider promotional discounts to boost sales. Monitor basket associations.";
        } else if (overallScore >= 40) {
            return "Low readiness - Review pricing strategy and cross-sell opportunities. May need aggressive discounts.";
        } else {
            return "Very low readiness - Consider reducing stock or implementing significant discounts. Review product positioning.";
        }
    }

    /**
     * Fuzzy product name matching.
     */
    private boolean matchesProductName(String dbProductName, String searchProductName) {
        if (dbProductName == null || searchProductName == null) {
            return false;
        }

        String normalizedDb = dbProductName.trim().toLowerCase();
        String normalizedSearch = searchProductName.trim().toLowerCase();

        if (normalizedDb.equals(normalizedSearch) || dbProductName.equalsIgnoreCase(searchProductName)) {
            return true;
        }

        String[] searchWords = normalizedSearch.split("\\s+");
        String[] dbWords = normalizedDb.split("\\s+");

        boolean allWordsMatch = true;
        for (String searchWord : searchWords) {
            if (searchWord.length() > 2) {
                boolean wordFound = false;
                for (String dbWord : dbWords) {
                    if (dbWord.startsWith(searchWord) || searchWord.startsWith(dbWord)) {
                        wordFound = true;
                        break;
                    }
                }
                if (!wordFound) {
                    allWordsMatch = false;
                    break;
                }
            }
        }

        return normalizedDb.startsWith(normalizedSearch) ||
                normalizedSearch.startsWith(normalizedDb) ||
                normalizedDb.contains(normalizedSearch) ||
                normalizedSearch.contains(normalizedDb) ||
                allWordsMatch;
    }
}

