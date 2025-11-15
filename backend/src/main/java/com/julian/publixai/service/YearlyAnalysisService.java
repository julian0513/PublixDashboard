package com.julian.publixai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.julian.publixai.model.Discount;
import com.julian.publixai.model.DiscountEffectiveness;
import com.julian.publixai.model.SaleRecord;
import com.julian.publixai.repository.DiscountEffectivenessRepository;
import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.repository.SaleRepository;

/**
 * YearlyAnalysisService
 * 
 * Purpose: Provide year-by-year analysis for products (2015-2025).
 * Returns sales data and active discounts per year.
 * 
 * This service enables managers to see historical trends and patterns
 * across the 10-year October/Halloween candy sales period.
 * 
 * Performance optimized: Uses indexed database queries instead of loading all
 * data into memory.
 */
@Service
public class YearlyAnalysisService {

    private final DiscountRepository discountRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final SaleRepository saleRepository;

    public YearlyAnalysisService(
            DiscountRepository discountRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            SaleRepository saleRepository) {
        this.discountRepository = discountRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.saleRepository = saleRepository;
    }

    /**
     * YearlyProductAnalysis DTO
     * Contains all analysis data for a product in a specific year.
     */
    public static class YearlyProductAnalysis {
        private final int year;
        private final String productName;
        private final Map<Integer, BigDecimal> weekDiscounts; // Week number -> discount percent (e.g., Week 2 -> 20%)
        private final Map<Integer, WeekDiscountInfo> weekDiscountDetails; // Week number -> discount details with date ranges
        private final List<DiscountEffectiveness> discountEffectiveness; // Discount performance data
        private final int totalUnitsSold; // Total units sold in October of that year
        private final BigDecimal totalRevenue; // Total revenue for October of that year
        
        /**
         * Week discount information with date range
         */
        public static class WeekDiscountInfo {
            private final BigDecimal discountPercent;
            private final LocalDate startDate;
            private final LocalDate endDate;
            
            public WeekDiscountInfo(BigDecimal discountPercent, LocalDate startDate, LocalDate endDate) {
                this.discountPercent = discountPercent;
                this.startDate = startDate;
                this.endDate = endDate;
            }
            
            public BigDecimal getDiscountPercent() { return discountPercent; }
            public LocalDate getStartDate() { return startDate; }
            public LocalDate getEndDate() { return endDate; }
        }

        public YearlyProductAnalysis(int year, String productName,
                Map<Integer, BigDecimal> weekDiscounts, List<DiscountEffectiveness> discountEffectiveness,
                int totalUnitsSold, BigDecimal totalRevenue) {
            this.year = year;
            this.productName = productName;
            this.weekDiscounts = weekDiscounts != null ? weekDiscounts : new HashMap<>();
            this.weekDiscountDetails = new HashMap<>(); // Will be populated separately
            this.discountEffectiveness = discountEffectiveness != null ? discountEffectiveness : new ArrayList<>();
            this.totalUnitsSold = totalUnitsSold;
            this.totalRevenue = totalRevenue;
        }
        
        public YearlyProductAnalysis(int year, String productName,
                Map<Integer, BigDecimal> weekDiscounts, Map<Integer, WeekDiscountInfo> weekDiscountDetails,
                List<DiscountEffectiveness> discountEffectiveness,
                int totalUnitsSold, BigDecimal totalRevenue) {
            this.year = year;
            this.productName = productName;
            this.weekDiscounts = weekDiscounts != null ? weekDiscounts : new HashMap<>();
            this.weekDiscountDetails = weekDiscountDetails != null ? weekDiscountDetails : new HashMap<>();
            this.discountEffectiveness = discountEffectiveness != null ? discountEffectiveness : new ArrayList<>();
            this.totalUnitsSold = totalUnitsSold;
            this.totalRevenue = totalRevenue;
        }

        public int getYear() {
            return year;
        }

        public String getProductName() {
            return productName;
        }

        public Map<Integer, BigDecimal> getWeekDiscounts() {
            return weekDiscounts;
        }
        
        public Map<Integer, WeekDiscountInfo> getWeekDiscountDetails() {
            return weekDiscountDetails;
        }

        public List<DiscountEffectiveness> getDiscountEffectiveness() {
            return discountEffectiveness;
        }

        public int getTotalUnitsSold() {
            return totalUnitsSold;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }

    /**
     * Get year-by-year analysis for a product (2015-2025).
     * Returns analysis for all years where data exists, including current year
     * (2025).
     * 
     * Performance optimized: Uses indexed database queries instead of findAll().
     * 
     * @param productName Product name (supports fuzzy matching)
     * @return List of yearly analyses, ordered by year ascending
     */
    @Transactional(readOnly = true)
    public List<YearlyProductAnalysis> getYearlyAnalysis(String productName) {
        List<YearlyProductAnalysis> analyses = new ArrayList<>();

        // Analyze each year from 2015 to 2025 (including current year)
        int currentYear = LocalDate.now().getYear();
        int endYear = Math.max(2024, currentYear); // At least 2024, or current year if later

        for (int year = 2015; year <= endYear; year++) {
            LocalDate yearStart = LocalDate.of(year, 10, 1);
            LocalDate yearEnd = LocalDate.of(year, 10, 31);

            // Get week-by-week discount summary for October of this year (optimized query)
            Map<Integer, BigDecimal> weekDiscounts = getWeekDiscountsForYear(productName, year);
            Map<Integer, YearlyProductAnalysis.WeekDiscountInfo> weekDiscountDetails = getWeekDiscountDetailsForYear(productName, year);

            // Get discount effectiveness data for this year (optimized query)
            List<DiscountEffectiveness> effectiveness = getDiscountEffectivenessForYear(productName, yearStart,
                    yearEnd);

            // Get sales data for October of this year (optimized query with product filter)
            int totalUnits = saleRepository.findByProductNameAndDateBetween(productName, yearStart, yearEnd)
                    .stream()
                    .mapToInt(SaleRecord::getUnits)
                    .sum();

            // Calculate revenue (simplified - using average price)
            BigDecimal avgPrice = BigDecimal.valueOf(3.50); // Average candy price
            BigDecimal totalRevenue = avgPrice.multiply(BigDecimal.valueOf(totalUnits));

            analyses.add(new YearlyProductAnalysis(year, productName, weekDiscounts, weekDiscountDetails, effectiveness,
                    totalUnits, totalRevenue));
        }

        return analyses;
    }

    /**
     * Get week-by-week discount summary for October of a specific year.
     * Returns a map of week number (1-5) to discount percent.
     * 
     * Performance optimized: Uses indexed database query instead of findAll().
     * 
     * Week calculation:
     * - Week 1: October 1-7
     * - Week 2: October 8-14
     * - Week 3: October 15-21
     * - Week 4: October 22-28
     * - Week 5: October 29-31
     * 
     * If multiple discounts exist in a week, uses the highest discount percent.
     */
    private Map<Integer, BigDecimal> getWeekDiscountsForYear(String productName, int year) {
        LocalDate yearStart = LocalDate.of(year, 10, 1);
        LocalDate yearEnd = LocalDate.of(year, 10, 31);

        // Use optimized query instead of findAll()
        List<Discount> matchingDiscounts = discountRepository.findDiscountsOverlappingRange(productName, yearStart,
                yearEnd);

        // If no exact match, try fuzzy matching
        if (matchingDiscounts.isEmpty()) {
            matchingDiscounts = discountRepository.findDiscountsOverlappingRangeFuzzy(productName, yearStart, yearEnd);
        }

        // Map to store week -> highest discount percent
        Map<Integer, BigDecimal> weekDiscounts = new HashMap<>();

        for (Discount discount : matchingDiscounts) {
            // Determine which weeks this discount covers
            LocalDate discountStart = discount.getStartDate().isBefore(yearStart) ? yearStart : discount.getStartDate();
            LocalDate discountEnd = discount.getEndDate().isAfter(yearEnd) ? yearEnd : discount.getEndDate();

            // Calculate weeks covered by this discount
            for (int day = discountStart.getDayOfMonth(); day <= discountEnd.getDayOfMonth(); day++) {
                int week = calculateWeekOfOctober(day);

                // Store the highest discount percent for each week
                weekDiscounts.merge(week, discount.getDiscountPercent(),
                        (existing, newValue) -> newValue.compareTo(existing) > 0 ? newValue : existing);
            }
        }

        return weekDiscounts;
    }
    
    /**
     * Get week-by-week discount details with date ranges for October of a specific year.
     * Returns a map of week number to WeekDiscountInfo containing discount percent and date range.
     */
    private Map<Integer, YearlyProductAnalysis.WeekDiscountInfo> getWeekDiscountDetailsForYear(String productName, int year) {
        LocalDate yearStart = LocalDate.of(year, 10, 1);
        LocalDate yearEnd = LocalDate.of(year, 10, 31);

        // Use optimized query
        List<Discount> matchingDiscounts = discountRepository.findDiscountsOverlappingRange(productName, yearStart, yearEnd);
        if (matchingDiscounts.isEmpty()) {
            matchingDiscounts = discountRepository.findDiscountsOverlappingRangeFuzzy(productName, yearStart, yearEnd);
        }

        // Map to store week -> discount info (with date range)
        Map<Integer, YearlyProductAnalysis.WeekDiscountInfo> weekDetails = new HashMap<>();
        
        // Map to track week -> best discount (highest percent, longest duration)
        Map<Integer, Discount> bestDiscountPerWeek = new HashMap<>();

        for (Discount discount : matchingDiscounts) {
            LocalDate discountStart = discount.getStartDate().isBefore(yearStart) ? yearStart : discount.getStartDate();
            LocalDate discountEnd = discount.getEndDate().isAfter(yearEnd) ? yearEnd : discount.getEndDate();

            // Calculate which weeks this discount covers
            for (int day = discountStart.getDayOfMonth(); day <= discountEnd.getDayOfMonth(); day++) {
                int week = calculateWeekOfOctober(day);
                
                // Store the best discount for each week (highest percent, or if equal, longer duration)
                Discount existing = bestDiscountPerWeek.get(week);
                if (existing == null || 
                    discount.getDiscountPercent().compareTo(existing.getDiscountPercent()) > 0 ||
                    (discount.getDiscountPercent().compareTo(existing.getDiscountPercent()) == 0 && 
                     discount.getEndDate().isAfter(existing.getEndDate()))) {
                    bestDiscountPerWeek.put(week, discount);
                }
            }
        }
        
        // Convert to WeekDiscountInfo with proper date ranges for each week
        for (Map.Entry<Integer, Discount> entry : bestDiscountPerWeek.entrySet()) {
            int week = entry.getKey();
            Discount discount = entry.getValue();
            
            // Calculate week date range
            LocalDate weekStart = LocalDate.of(year, 10, getWeekStartDay(week));
            LocalDate weekEnd = LocalDate.of(year, 10, getWeekEndDay(week));
            
            // Use discount dates if they're within the week, otherwise use week boundaries
            LocalDate infoStart = discount.getStartDate().isAfter(weekStart) ? discount.getStartDate() : weekStart;
            LocalDate infoEnd = discount.getEndDate().isBefore(weekEnd) ? discount.getEndDate() : weekEnd;
            
            weekDetails.put(week, new YearlyProductAnalysis.WeekDiscountInfo(
                discount.getDiscountPercent(), 
                infoStart, 
                infoEnd
            ));
        }

        return weekDetails;
    }
    
    /**
     * Get the start day of a week (1-based)
     */
    private int getWeekStartDay(int week) {
        return switch (week) {
            case 1 -> 1;
            case 2 -> 8;
            case 3 -> 15;
            case 4 -> 22;
            case 5 -> 29;
            default -> 1;
        };
    }
    
    /**
     * Get the end day of a week (1-based)
     */
    private int getWeekEndDay(int week) {
        return switch (week) {
            case 1 -> 7;
            case 2 -> 14;
            case 3 -> 21;
            case 4 -> 28;
            case 5 -> 31;
            default -> 31;
        };
    }

    /**
     * Calculate which week of October a day falls into.
     * Week 1: Days 1-7
     * Week 2: Days 8-14
     * Week 3: Days 15-21
     * Week 4: Days 22-28
     * Week 5: Days 29-31
     */
    private int calculateWeekOfOctober(int day) {
        if (day <= 7)
            return 1;
        if (day <= 14)
            return 2;
        if (day <= 21)
            return 3;
        if (day <= 28)
            return 4;
        return 5; // Days 29-31
    }

    /**
     * Get discount effectiveness data for a product in October of a specific year.
     * Performance optimized: Uses indexed database query instead of findAll().
     */
    private List<DiscountEffectiveness> getDiscountEffectivenessForYear(String productName, LocalDate yearStart,
            LocalDate yearEnd) {
        // Use optimized query with exact match first
        List<DiscountEffectiveness> allEffectiveness = discountEffectivenessRepository
                .findByProductNameAndDateBetween(productName, yearStart, yearEnd);

        // If no exact match, try fuzzy matching with optimized query
        if (allEffectiveness.isEmpty()) {
            allEffectiveness = discountEffectivenessRepository
                    .findByProductNameFuzzyAndDateBetween(productName, yearStart, yearEnd);
        }

        return allEffectiveness;
    }

}
