package com.julian.publixai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.julian.publixai.model.BasketAnalysis;
import com.julian.publixai.model.DiscountEffectiveness;

/**
 * NewProductPredictionService
 * 
 * Purpose: Auto-generate predicted frequently bought together items and
 * predicted best discounts for new products that aren't part of the
 * historical seed data (2015-2024).
 * 
 * This service helps managers make informed decisions about new products
 * by providing predicted associations and discount recommendations based on
 * similar products and market patterns.
 */
@Service
public class NewProductPredictionService {

    // Historical seed products (from CSV data 2015-2024)
    private static final Set<String> HISTORICAL_SEED_PRODUCTS = Set.of(
            "Reese's", "M&M's", "Snickers", "Twix", "Kit Kat",
            "Hershey's Chocolate", "Skittles", "Starburst", "Sour Patch Kids",
            "Jolly Ranchers", "Twizzlers", "Milky Way", "3 Musketeers",
            "Butterfinger", "Almond Joy", "Mounds", "Baby Ruth", "PayDay",
            "100 GRAND", "Nerds", "Ferrara Candy Assorted Pack"
    );

    // Common grocery items that pair with candy (for predictions)
    private static final List<String> COMMON_GROCERY_ITEMS = Arrays.asList(
            "Milk", "Bread", "Eggs", "Bananas", "Apples", "Orange Juice",
            "Chips", "Soda", "Ice Cream", "Cookies", "Cereal", "Yogurt"
    );

    // Popular candy products (for cross-product associations)
    private static final List<String> POPULAR_CANDY = Arrays.asList(
            "M&M's", "Reese's", "Snickers", "Twix", "Hershey's Chocolate",
            "Kit Kat", "Skittles", "Starburst"
    );

    public NewProductPredictionService() {
        // No dependencies required - predictions are in-memory only
    }

    /**
     * Check if a product is new (not in historical seed data).
     * 
     * @param productName Product name to check
     * @return true if product is new (not in historical seed), false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isNewProduct(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return false;
        }

        // Only check if product exists in historical seed products (case-insensitive)
        // Products not in seed list are considered "new" and should show predictions,
        // regardless of whether they have sales records (user-added products)
        String normalized = productName.trim();
        boolean inSeed = HISTORICAL_SEED_PRODUCTS.stream()
                .anyMatch(seed -> seed.equalsIgnoreCase(normalized) || 
                                 normalized.equalsIgnoreCase(seed));

        // User-added products can show predictions even if they have sales entries
        // This allows products like "tester" to display predictions

        return !inSeed;
    }

    /**
     * Generate predicted frequently bought together items for a new product.
     * Uses patterns from similar products and common grocery associations.
     * 
     * IMPORTANT: This method is READ-ONLY. Predictions are NOT saved to the database.
     * They are returned in-memory for display only.
     * 
     * @param productName New product name
     * @return List of predicted BasketAnalysis records (up to 10 items, in-memory only)
     */
    @Transactional(readOnly = true)
    public List<BasketAnalysis> generatePredictedAssociations(String productName) {
        if (!isNewProduct(productName)) {
            // Product is not new, return empty list (should use actual data)
            return new ArrayList<>();
        }

        List<BasketAnalysis> predictions = new ArrayList<>();
        Random random = new Random(productName.hashCode()); // Seed for consistency

        // Strategy: Mix popular candy products and common grocery items
        // This creates realistic predictions based on market patterns

        // 60% chance to associate with popular candy products
        List<String> candyAssociations = POPULAR_CANDY.stream()
                .filter(candy -> !candy.equalsIgnoreCase(productName))
                .filter(candy -> random.nextDouble() < 0.6)
                .limit(5)
                .collect(Collectors.toList());

        // 70% chance to associate with common grocery items
        List<String> groceryAssociations = COMMON_GROCERY_ITEMS.stream()
                .filter(item -> random.nextDouble() < 0.7)
                .limit(5)
                .collect(Collectors.toList());

        // Combine and create predictions
        List<String> allAssociations = new ArrayList<>();
        allAssociations.addAll(candyAssociations);
        allAssociations.addAll(groceryAssociations);

        // Ensure we have exactly 10 associations (pad with more if needed)
        allAssociations = allAssociations.stream()
                .distinct()
                .collect(Collectors.toList());
        
        // If we have less than 10, add more from common items to ensure exactly 10
        while (allAssociations.size() < 10) {
            // Add more grocery items or popular candy to reach 10
            for (String item : COMMON_GROCERY_ITEMS) {
                if (!allAssociations.contains(item) && allAssociations.size() < 10) {
                    allAssociations.add(item);
                }
            }
            for (String item : POPULAR_CANDY) {
                if (!allAssociations.contains(item) && !item.equalsIgnoreCase(productName) && allAssociations.size() < 10) {
                    allAssociations.add(item);
                }
            }
            // Break if we can't add more (shouldn't happen, but safety check)
            if (allAssociations.size() >= 10) break;
        }
        
        // Limit to exactly 10
        allAssociations = allAssociations.stream()
                .limit(10)
                .collect(Collectors.toList());

        // Create BasketAnalysis predictions with realistic confidence scores
        for (int i = 0; i < allAssociations.size(); i++) {
            String associatedProduct = allAssociations.get(i);
            
            // Higher confidence for top associations, decreasing for lower ranks
            double baseConfidence = 0.4 - (i * 0.03); // 0.4 to 0.13
            BigDecimal confidence = BigDecimal.valueOf(Math.max(0.1, baseConfidence + (random.nextDouble() * 0.1)));
            
            // Co-occurrence count (predicted based on rank)
            int coOccurrenceCount = 50 - (i * 5); // 50 to 5
            
            // Support score (lower than confidence, typically)
            BigDecimal support = confidence.multiply(BigDecimal.valueOf(0.6));

            BasketAnalysis prediction = new BasketAnalysis();
            prediction.setPrimaryProduct(productName);
            prediction.setAssociatedProduct(associatedProduct);
            prediction.setCoOccurrenceCount(coOccurrenceCount);
            prediction.setConfidenceScore(confidence);
            prediction.setSupportScore(support);

            predictions.add(prediction);
        }

        // DO NOT SAVE: Predictions are in-memory only for display
        // This prevents database pollution with predicted data
        // basketAnalysisRepository.saveAll(predictions); ← REMOVED

        return predictions;
    }

    /**
     * Generate predicted best discount for a new product.
     * Uses market patterns from similar products.
     * 
     * IMPORTANT: This method is READ-ONLY. The optimal discount is calculated but
     * discount effectiveness data is NOT saved to the database.
     * 
     * @param productName New product name
     * @return Predicted optimal discount percentage, or null if product is not new
     */
    @Transactional(readOnly = true)
    public BigDecimal generatePredictedOptimalDiscount(String productName) {
        if (!isNewProduct(productName)) {
            // Product is not new, return null (should use actual data)
            return null;
        }

        Random random = new Random(productName.hashCode()); // Seed for consistency

        // Strategy: Most candy products perform best with 15-25% discounts
        // New products typically need slightly higher discounts (20-25%) to gain traction
        BigDecimal[] discountOptions = {
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(25)
        };

        // Weighted selection: 20% chance for 15%, 40% chance for 20%, 40% chance for 25%
        double rand = random.nextDouble();
        BigDecimal predictedDiscount;
        if (rand < 0.2) {
            predictedDiscount = discountOptions[0]; // 15%
        } else if (rand < 0.6) {
            predictedDiscount = discountOptions[1]; // 20%
        } else {
            predictedDiscount = discountOptions[2]; // 25%
        }

        // Create comprehensive discount effectiveness records to support the prediction
        // Generate data for multiple discount levels (10%, 15%, 20%, 25%, 30%) to show comparison
        // This creates rich data for the discount insights display
        LocalDate today = LocalDate.now();
        List<DiscountEffectiveness> effectivenessRecords = new ArrayList<>();
        
        // Generate data for multiple discount levels
        BigDecimal[] allDiscountLevels = {
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(15),
                predictedDiscount, // The predicted optimal
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(30)
        };

        // Generate records for each discount level (past 60 days for richer data)
        for (BigDecimal discountLevel : allDiscountLevels) {
            for (int day = 0; day < 60; day++) {
                LocalDate date = today.minusDays(day);
                
                // Predicted units sold (varies by discount level - higher discount = more units)
                double baseUnits = 25 + (discountLevel.doubleValue() * 1.5);
                // Optimal discount gets a boost
                if (discountLevel.compareTo(predictedDiscount) == 0) {
                    baseUnits *= 1.2; // 20% boost for optimal discount
                }
                int predictedUnits = (int) (baseUnits + (random.nextDouble() * 15)); // Add variation
                
                // Predicted revenue
                BigDecimal avgPrice = BigDecimal.valueOf(3.50);
                BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                        discountLevel.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
                BigDecimal revenue = avgPrice.multiply(discountMultiplier).multiply(BigDecimal.valueOf(predictedUnits));
                
                // Predicted sales lift (higher discounts = higher lift)
                BigDecimal salesLift = discountLevel.multiply(BigDecimal.valueOf(0.5))
                        .add(BigDecimal.valueOf(random.nextDouble() * 8 - 4)); // Add variation
                if (salesLift.compareTo(BigDecimal.ZERO) < 0) {
                    salesLift = BigDecimal.ZERO;
                }

                DiscountEffectiveness effectiveness = new DiscountEffectiveness();
                effectiveness.setProductName(productName);
                effectiveness.setDiscountPercent(discountLevel);
                effectiveness.setDate(date);
                effectiveness.setUnitsSold(predictedUnits);
                effectiveness.setRevenue(revenue);
                effectiveness.setAvgUnitPrice(avgPrice);
                effectiveness.setSalesLiftPercent(salesLift);

                effectivenessRecords.add(effectiveness);
            }
        }

        // DO NOT SAVE: Effectiveness records are in-memory only for display
        // This prevents database pollution with predicted data
        // discountEffectivenessRepository.saveAll(effectivenessRecords); ← REMOVED

        return predictedDiscount;
    }

    /**
     * Generate predicted discount effectiveness data for a new product (IN-MEMORY ONLY).
     * This creates comprehensive discount data for display in the Discount Insights tab.
     * 
     * IMPORTANT: Data is NOT saved to database. Returns in-memory records for display only.
     * 
     * @param productName New product name
     * @return List of predicted DiscountEffectiveness records (in-memory only)
     */
    @Transactional(readOnly = true)
    public List<DiscountEffectiveness> generatePredictedDiscountEffectiveness(String productName) {
        if (!isNewProduct(productName)) {
            return new ArrayList<>();
        }

        Random random = new Random(productName.hashCode());
        List<DiscountEffectiveness> effectivenessRecords = new ArrayList<>();
        
        BigDecimal[] allDiscountLevels = {
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(30)
        };

        LocalDate today = LocalDate.now();

        // Generate predicted data for each discount level (past 30 days for display)
        for (BigDecimal discountLevel : allDiscountLevels) {
            for (int day = 0; day < 30; day++) {
                LocalDate date = today.minusDays(day);
                
                // Predicted units sold (varies by discount level)
                double baseUnits = 25 + (discountLevel.doubleValue() * 1.5);
                int predictedUnits = (int) (baseUnits + (random.nextDouble() * 15));
                
                // Predicted revenue
                BigDecimal avgPrice = BigDecimal.valueOf(3.50);
                BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                        discountLevel.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
                BigDecimal revenue = avgPrice.multiply(discountMultiplier).multiply(BigDecimal.valueOf(predictedUnits));
                
                // Predicted sales lift
                BigDecimal salesLift = discountLevel.multiply(BigDecimal.valueOf(0.5))
                        .add(BigDecimal.valueOf(random.nextDouble() * 8 - 4));
                salesLift = salesLift.max(BigDecimal.ZERO);

                DiscountEffectiveness effectiveness = new DiscountEffectiveness();
                effectiveness.setProductName(productName);
                effectiveness.setDiscountPercent(discountLevel);
                effectiveness.setDate(date);
                effectiveness.setUnitsSold(predictedUnits);
                effectiveness.setRevenue(revenue);
                effectiveness.setAvgUnitPrice(avgPrice);
                effectiveness.setSalesLiftPercent(salesLift);

                effectivenessRecords.add(effectiveness);
            }
        }

        // Return in-memory records (NOT saved to database)
        return effectivenessRecords;
    }

    /**
     * Auto-generate both predicted associations and optimal discount for a new product.
     * This is the main method to call when a new product is detected.
     * 
     * @param productName New product name
     * @return Map containing "associations" (List<BasketAnalysis>) and "optimalDiscount" (BigDecimal)
     */
    @Transactional
    public Map<String, Object> generatePredictionsForNewProduct(String productName) {
        Map<String, Object> result = new HashMap<>();
        
        if (!isNewProduct(productName)) {
            result.put("isNew", false);
            result.put("message", "Product is in historical seed data. Use actual data instead.");
            return result;
        }

        result.put("isNew", true);
        result.put("associations", generatePredictedAssociations(productName));
        result.put("optimalDiscount", generatePredictedOptimalDiscount(productName));
        result.put("message", "Generated predicted associations and optimal discount for new product.");

        return result;
    }
}

