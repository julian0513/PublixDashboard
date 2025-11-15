package com.julian.publixai.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.julian.publixai.model.SaleRecord;
import com.julian.publixai.repository.SaleRepository;

import jakarta.validation.constraints.NotBlank;

/**
 * ProductTrendController
 * 
 * REST API for product trend analysis (10-year sparkline data).
 * Provides historical sales data for visualization in hover tooltips.
 * 
 * Endpoints:
 * - GET /api/sales/trend?productName={name} - Get 10-year trend (2015-2024)
 */
@RestController
@RequestMapping("/api/sales")
@Validated
public class ProductTrendController {

    private final SaleRepository saleRepository;

    public ProductTrendController(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    /**
     * TrendDataPoint DTO
     * Represents sales data for a single year.
     */
    public static class TrendDataPoint {
        private final int year;
        private final int units;

        public TrendDataPoint(int year, int units) {
            this.year = year;
            this.units = units;
        }

        public int getYear() {
            return year;
        }

        public int getUnits() {
            return units;
        }
    }

    /**
     * GET /api/sales/trend?productName={name}&mode={seed|live}
     * Get trend data for a product.
     * 
     * - mode="seed" (historical baseline): Returns 2015-2024 only (locked historical data)
     * - mode="live" (current predictions): Returns 2015-2025 including current year data
     * 
     * For new products, generates trend based on current sales performance (only for live mode).
     * 
     * @param productName Product name (supports fuzzy matching via repository)
     * @param mode Optional mode: "seed" for historical baseline (2015-2024), "live" for current (2015-2025)
     * @return List of trend data points, one per year
     */
    @GetMapping("/trend")
    public List<TrendDataPoint> getProductTrend(
            @RequestParam("productName") @NotBlank String productName,
            @RequestParam(value = "mode", required = false, defaultValue = "seed") String mode) {

        List<TrendDataPoint> trendData = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();
        
        // Historical baseline (seed mode): Only 2015-2024, locked to historical data
        // Current predictions (live mode): 2015-2025, includes current year
        boolean isHistorical = "seed".equalsIgnoreCase(mode) || "historical".equalsIgnoreCase(mode) || "baseline".equalsIgnoreCase(mode);
        int endYear = isHistorical ? 2024 : Math.max(2024, currentYear);

        // Get data for each year
        for (int year = 2015; year <= endYear; year++) {
            LocalDate yearStart = LocalDate.of(year, 10, 1);
            LocalDate yearEnd = LocalDate.of(year, 10, 31);
            
            // For current year in live mode, use today's date as end date if we're still in October
            if (!isHistorical && year == currentYear && LocalDate.now().getMonthValue() == 10) {
                yearEnd = LocalDate.now();
            }

            // Get all sales for October of this year
            List<SaleRecord> sales = saleRepository.findByDateBetween(yearStart, yearEnd);

            // Sum units for this product (fuzzy matching)
            int totalUnits = sales.stream()
                    .filter(s -> matchesProductName(s.getProductName(), productName))
                    .mapToInt(SaleRecord::getUnits)
                    .sum();

            trendData.add(new TrendDataPoint(year, totalUnits));
        }

        // For new products with no historical data, generate trend from current sales
        // ONLY for live mode (current predictions), NOT for historical baseline
        if (!isHistorical) {
            boolean hasAnyData = trendData.stream().anyMatch(td -> td.getUnits() > 0);
            
            if (!hasAnyData) {
                // This is likely a new product - generate trend based on current sales
                // Get all sales records for this product (any date)
                List<SaleRecord> allSales = saleRepository.findAll();
                List<SaleRecord> productSales = allSales.stream()
                        .filter(s -> matchesProductName(s.getProductName(), productName))
                        .collect(java.util.stream.Collectors.toList());
                
                if (!productSales.isEmpty()) {
                    // Calculate average daily sales from current data
                    double avgDailySales = productSales.stream()
                            .mapToInt(SaleRecord::getUnits)
                            .average()
                            .orElse(0.0);
                    
                    // Generate trend: start low, build up to current average
                    // This creates a realistic growth pattern for new products
                    trendData.clear();
                    for (int year = 2015; year <= endYear; year++) {
                        int units;
                        if (year < currentYear - 1) {
                            // Historical years: very low (new product)
                            units = (int) (avgDailySales * 0.1 * 31); // 10% of current average
                        } else if (year == currentYear - 1) {
                            // Last year: moderate
                            units = (int) (avgDailySales * 0.5 * 31); // 50% of current average
                        } else {
                            // Current year: use actual average
                            units = (int) (avgDailySales * 31); // Full average for October
                        }
                        trendData.add(new TrendDataPoint(year, units));
                    }
                }
            }
        }

        return trendData;
    }

    /**
     * Fuzzy product name matching.
     * Handles variations like "Reese's" matching "Reese's Peanut Butter Cups".
     */
    private boolean matchesProductName(String dbProductName, String searchProductName) {
        if (dbProductName == null || searchProductName == null) {
            return false;
        }

        String normalizedDb = dbProductName.trim().toLowerCase();
        String normalizedSearch = searchProductName.trim().toLowerCase();

        // Exact match
        if (normalizedDb.equals(normalizedSearch)) {
            return true;
        }

        // Case-insensitive exact match
        if (dbProductName.equalsIgnoreCase(searchProductName)) {
            return true;
        }

        // Word-based matching
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

