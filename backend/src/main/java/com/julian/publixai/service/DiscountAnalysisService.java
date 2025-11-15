package com.julian.publixai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.julian.publixai.dto.DiscountEffectivenessResponse;
import com.julian.publixai.model.Discount;
import com.julian.publixai.model.DiscountEffectiveness;
import com.julian.publixai.model.SaleRecord;
import com.julian.publixai.repository.DiscountEffectivenessRepository;
import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.repository.SaleRepository;

/**
 * DiscountAnalysisService
 * 
 * Purpose: Analyze discount effectiveness and find optimal discount
 * percentages.
 */
@Service
public class DiscountAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DiscountAnalysisService.class);

    private final DiscountRepository discountRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final SaleRepository saleRepository;
    private final NewProductPredictionService newProductPredictionService;

    public DiscountAnalysisService(
            DiscountRepository discountRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            SaleRepository saleRepository,
            NewProductPredictionService newProductPredictionService) {
        this.discountRepository = discountRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.saleRepository = saleRepository;
        this.newProductPredictionService = newProductPredictionService;
    }

    @Transactional(readOnly = true)
    public List<Discount> getActiveDiscounts(LocalDate date) {
        return discountRepository.findActiveDiscounts(date);
    }

    @Transactional(readOnly = true)
    public List<Discount> getActiveDiscountsForProduct(String productName, LocalDate date) {
        return discountRepository.findActiveDiscountsForProduct(productName, date);
    }

    /**
     * Get discount effectiveness data for a product.
     * For new products, generates predicted discount effectiveness data.
     */
    @Transactional
    public DiscountEffectivenessResponse getDiscountEffectiveness(String productName) {
        List<DiscountEffectiveness> effectiveness = discountEffectivenessRepository
                .findByProductNameOrderByUnitsSoldDesc(productName);

        // If no exact match, try partial matching
        if (effectiveness.isEmpty()) {
            effectiveness = getDiscountEffectivenessPartialMatch(productName);
        }

        boolean isPredicted = false;
        BigDecimal optimalDiscount = null;

        // If still no data and product is new, generate predictions (IN-MEMORY ONLY)
        if (effectiveness.isEmpty() && newProductPredictionService.isNewProduct(productName)) {
            // Generate predicted discount effectiveness data (returns in-memory, doesn't save to DB)
            effectiveness = newProductPredictionService.generatePredictedDiscountEffectiveness(productName);
            optimalDiscount = newProductPredictionService.generatePredictedOptimalDiscount(productName);
            isPredicted = true;
        } else if (!effectiveness.isEmpty()) {
            optimalDiscount = findOptimalDiscount(productName);
            isPredicted = false;
        }

        return new DiscountEffectivenessResponse(effectiveness, isPredicted, optimalDiscount);
    }

    /**
     * Get discount effectiveness with partial product name matching.
     * Uses word-based matching similar to basket analysis (e.g., "Ferrara" matches "Ferrara Candy Assorted Pack").
     */
    @Transactional(readOnly = true)
    public List<DiscountEffectiveness> getDiscountEffectivenessPartialMatch(String productName) {
        List<DiscountEffectiveness> all = discountEffectivenessRepository.findAll();
        String normalizedSearch = productName.trim().toLowerCase();
        return all.stream()
                .filter(de -> {
                    String normalizedProduct = de.getProductName().trim().toLowerCase();
                    
                    // Word-based matching (e.g., "Ferrara" matches "Ferrara Candy Assorted Pack")
                    String[] searchWords = normalizedSearch.split("\\s+");
                    String[] productWords = normalizedProduct.split("\\s+");
                    
                    boolean allWordsMatch = true;
                    for (String searchWord : searchWords) {
                        if (searchWord.length() > 2) { // Only check words longer than 2 chars
                            boolean wordFound = false;
                            for (String productWord : productWords) {
                                if (productWord.startsWith(searchWord) || searchWord.startsWith(productWord)) {
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
                    
                    return normalizedProduct.startsWith(normalizedSearch) ||
                            normalizedSearch.startsWith(normalizedProduct) ||
                            normalizedProduct.contains(normalizedSearch) ||
                            normalizedSearch.contains(normalizedProduct) ||
                            allWordsMatch;
                })
                .sorted((a, b) -> Integer.compare(b.getUnitsSold(), a.getUnitsSold()))
                .collect(Collectors.toList());
    }

    /**
     * Count total number of discount effectiveness records.
     * Used to check if any data exists globally.
     */
    @Transactional(readOnly = true)
    public long countAllDiscountEffectiveness() {
        return discountEffectivenessRepository.count();
    }

    @Transactional(readOnly = true)
    public List<DiscountEffectiveness> getDiscountEffectiveness(String productName, LocalDate start, LocalDate end) {
        return discountEffectivenessRepository.findByProductNameAndDateBetween(productName, start, end);
    }

    /**
     * Find the optimal discount percentage for a product based on units sold.
     * For new products, generates predicted optimal discount.
     */
    @Transactional
    public BigDecimal findOptimalDiscount(String productName) {
        List<DiscountEffectiveness> effectiveness = discountEffectivenessRepository
                .findByProductNameOrderByUnitsSoldDesc(productName);

        // If no exact match, try partial matching
        if (effectiveness.isEmpty()) {
            effectiveness = getDiscountEffectivenessPartialMatch(productName);
        }

        // If still no data and product is new, generate prediction (IN-MEMORY ONLY)
        if (effectiveness.isEmpty() && newProductPredictionService.isNewProduct(productName)) {
            BigDecimal predictedDiscount = newProductPredictionService.generatePredictedOptimalDiscount(productName);
            if (predictedDiscount != null) {
                return predictedDiscount;
            }
        }

        if (effectiveness.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Group by discount percent and calculate average sales lift
        // Sales lift is the key metric - it shows how much the discount increases sales
        Map<BigDecimal, Double> discountToAvgLift = effectiveness.stream()
                .filter(de -> de.getSalesLiftPercent() != null && de.getSalesLiftPercent().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        DiscountEffectiveness::getDiscountPercent,
                        Collectors.averagingDouble(de -> de.getSalesLiftPercent().doubleValue())));

        if (discountToAvgLift.isEmpty()) {
            // Fallback: If no sales lift data, use average units sold
            Map<BigDecimal, Double> discountToAvgUnits = effectiveness.stream()
                    .collect(Collectors.groupingBy(
                            DiscountEffectiveness::getDiscountPercent,
                            Collectors.averagingInt(DiscountEffectiveness::getUnitsSold)));
            return discountToAvgUnits.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(BigDecimal.ZERO);
        }

        // Find discount with highest average sales lift
        return discountToAvgLift.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculate sales lift for a product with a discount compared to baseline (no
     * discount).
     */
    @Transactional
    public void calculateDiscountEffectiveness(String productName, LocalDate date,
            BigDecimal discountPercent, int unitsSold,
            BigDecimal avgUnitPrice) {
        // Get baseline (average units sold without discount for this product)
        List<SaleRecord> baselineSales = saleRepository.findByDate(date);
        int baselineUnits = baselineSales.stream()
                .filter(s -> s.getProductName().equals(productName))
                .mapToInt(SaleRecord::getUnits)
                .sum();

        // If no baseline, use a default or skip calculation
        if (baselineUnits == 0) {
            baselineUnits = 1; // Avoid division by zero
        }

        // Calculate sales lift
        BigDecimal salesLift = BigDecimal.valueOf(unitsSold - baselineUnits)
                .divide(BigDecimal.valueOf(baselineUnits), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Calculate revenue
        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal revenue = avgUnitPrice.multiply(discountMultiplier)
                .multiply(BigDecimal.valueOf(unitsSold));

        // Save or update effectiveness record
        DiscountEffectiveness effectiveness = discountEffectivenessRepository
                .findByProductNameAndDateBetween(productName, date, date)
                .stream()
                .filter(de -> de.getDiscountPercent().compareTo(discountPercent) == 0)
                .findFirst()
                .orElse(new DiscountEffectiveness());

        effectiveness.setProductName(productName);
        effectiveness.setDiscountPercent(discountPercent);
        effectiveness.setDate(date);
        effectiveness.setUnitsSold(unitsSold);
        effectiveness.setRevenue(revenue);
        effectiveness.setAvgUnitPrice(avgUnitPrice);
        effectiveness.setSalesLiftPercent(salesLift);

        discountEffectivenessRepository.save(effectiveness);
    }

    @Transactional
    public Discount createDiscount(String productName, BigDecimal discountPercent,
            LocalDate startDate, LocalDate endDate, String description) {
        Discount discount = new Discount();
        discount.setProductName(productName);
        discount.setDiscountPercent(discountPercent);
        discount.setStartDate(startDate);
        discount.setEndDate(endDate);
        discount.setDescription(description);
        return discountRepository.save(discount);
    }

    /**
     * Generate Discount records from existing discount_effectiveness records.
     * This creates Discount records for year-by-year display by extracting unique
     * (product_name, date, discount_percent) combinations from discount_effectiveness.
     * 
     * Each discount record represents a promotion period (3-7 days) starting from the date
     * found in discount_effectiveness.
     */
    @Transactional
    public void generateDiscountRecordsFromEffectiveness() {
        // Get all discount effectiveness records
        List<DiscountEffectiveness> allEffectiveness = discountEffectivenessRepository.findAll();
        
        if (allEffectiveness.isEmpty()) {
            log.warn("No discount effectiveness records found. Cannot generate Discount records.");
            return;
        }

        // Get existing discounts to avoid duplicates
        List<Discount> existingDiscounts = discountRepository.findAll();
        Set<String> existingKeys = existingDiscounts.stream()
                .map(d -> d.getProductName() + "|" + d.getStartDate() + "|" + d.getDiscountPercent())
                .collect(Collectors.toSet());

        // Group by (product_name, date, discount_percent) to get unique combinations
        // Use a Map to track unique combinations and create Discount records
        Map<String, DiscountEffectiveness> uniqueCombinations = allEffectiveness.stream()
                .collect(Collectors.toMap(
                        de -> de.getProductName() + "|" + de.getDate() + "|" + de.getDiscountPercent(),
                        de -> de,
                        (existing, replacement) -> existing // Keep first occurrence if duplicate
                ));

        List<Discount> newDiscounts = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random();

        for (DiscountEffectiveness de : uniqueCombinations.values()) {
            // Create a key to check if this discount already exists
            String key = de.getProductName() + "|" + de.getDate() + "|" + de.getDiscountPercent();
            
            if (!existingKeys.contains(key)) {
                // Create Discount record with 3-7 day duration
                int duration = 3 + random.nextInt(5); // 3-7 days
                LocalDate endDate = de.getDate().plusDays(duration - 1);
                
                Discount discount = new Discount();
                discount.setProductName(de.getProductName());
                discount.setDiscountPercent(de.getDiscountPercent());
                discount.setStartDate(de.getDate());
                discount.setEndDate(endDate);
                discount.setDescription("Halloween promotion - " + de.getDiscountPercent() + "% off");
                
                newDiscounts.add(discount);
                existingKeys.add(key); // Track in-memory to avoid duplicates in this batch
            }
        }

        if (!newDiscounts.isEmpty()) {
            discountRepository.saveAll(newDiscounts);
            log.info("Generated {} Discount records from {} unique discount_effectiveness combinations. Total in database: {}", 
                    newDiscounts.size(), uniqueCombinations.size(), discountRepository.count());
        } else {
            log.info("All Discount records already exist. No new records created.");
        }
    }
}
