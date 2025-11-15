package com.julian.publixai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.julian.publixai.model.BasketAnalysis;
import com.julian.publixai.model.Transaction;
import com.julian.publixai.model.TransactionItem;
import com.julian.publixai.repository.BasketAnalysisRepository;
import com.julian.publixai.repository.TransactionItemRepository;
import com.julian.publixai.repository.TransactionRepository;

/**
 * SimpleSyntheticDataService
 * 
 * Purpose: Generate simple, reliable synthetic transaction data and frequently bought together items.
 * 
 * This service generates exactly 3,000 transactions and creates synthetic frequently bought
 * together associations directly. No complex logic, no date ranges - just simple, reliable data.
 */
@Service
public class SimpleSyntheticDataService {

    private static final Logger log = LoggerFactory.getLogger(SimpleSyntheticDataService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final BasketAnalysisRepository basketAnalysisRepository;
    private final com.julian.publixai.repository.DiscountEffectivenessRepository discountEffectivenessRepository;
    private final com.julian.publixai.repository.DiscountRepository discountRepository;

    // Common candy products
    private static final List<String> CANDY_PRODUCTS = Arrays.asList(
            "Reese's", "Snickers", "M&M's", "Kit Kat", "Twix",
            "Hershey's Chocolate", "Skittles", "Starburst", "Sour Patch Kids",
            "Jolly Ranchers", "Twizzlers", "Milky Way", "3 Musketeers", "Butterfinger",
            "Almond Joy", "Mounds", "Baby Ruth", "PayDay", "100 GRAND", "Nerds",
            "Ferrara Candy Assorted Pack");

    // Common grocery items that pair with candy
    private static final List<String> GROCERY_ITEMS = Arrays.asList(
            "Milk", "Bread", "Eggs", "Bananas", "Apples", "Orange Juice",
            "Chips", "Soda", "Ice Cream", "Cookies", "Cereal", "Yogurt");

    // Predefined associations (candy -> frequently bought items)
    // Each candy has 10 associated items for top 10 display
    private static final Map<String, List<String>> ASSOCIATIONS = createAssociationsMap();

    private static Map<String, List<String>> createAssociationsMap() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("Reese's", Arrays.asList("M&M's", "Snickers", "Hershey's Chocolate", "Milk", "Ice Cream", "Cookies",
                "Soda", "Chips", "Bananas", "Orange Juice"));
        map.put("Snickers", Arrays.asList("Reese's", "M&M's", "Twix", "Soda", "Milk", "Chips", "Cookies", "Ice Cream",
                "Bananas", "Orange Juice"));
        map.put("M&M's", Arrays.asList("Reese's", "Snickers", "Twix", "Soda", "Chips", "Ice Cream", "Milk", "Cookies",
                "Bananas", "Orange Juice"));
        map.put("Kit Kat", Arrays.asList("Milk", "Coffee", "Cookies", "M&M's", "Soda", "Chips", "Ice Cream", "Bananas",
                "Orange Juice", "Bread"));
        map.put("Twix", Arrays.asList("M&M's", "Snickers", "Milky Way", "Soda", "Milk", "Chips", "Cookies", "Ice Cream",
                "Bananas", "Orange Juice"));
        map.put("Hershey's Chocolate", Arrays.asList("Reese's", "M&M's", "Milk", "Ice Cream", "Bananas", "Cookies",
                "Soda", "Chips", "Orange Juice", "Bread"));
        map.put("Milky Way", Arrays.asList("Twix", "3 Musketeers", "M&M's", "Soda", "Milk", "Chips", "Cookies",
                "Ice Cream", "Bananas", "Orange Juice"));
        map.put("3 Musketeers", Arrays.asList("Milky Way", "Twix", "M&M's", "Soda", "Milk", "Chips", "Cookies",
                "Ice Cream", "Bananas", "Orange Juice"));
        map.put("Skittles", Arrays.asList("Soda", "Chips", "Juice", "M&M's", "Milk", "Cookies", "Ice Cream", "Bananas",
                "Orange Juice", "Yogurt"));
        map.put("Starburst", Arrays.asList("Soda", "Chips", "Yogurt", "Skittles", "M&M's", "Milk", "Cookies",
                "Ice Cream", "Bananas", "Orange Juice"));
        map.put("Ferrara Candy Assorted Pack", Arrays.asList("M&M's", "Reese's", "Snickers", "Milk", "Cookies",
                "Ice Cream", "Soda", "Chips", "Bananas", "Orange Juice"));
        map.put("Nerds", Arrays.asList("M&M's", "Skittles", "Starburst", "Soda", "Chips", "Juice", "Milk", "Cookies",
                "Ice Cream", "Bananas"));
        map.put("100 GRAND", Arrays.asList("M&M's", "Snickers", "Reese's", "Milk", "Ice Cream", "Cookies", "Soda",
                "Chips", "Bananas", "Orange Juice"));
        
        // Add associations for remaining products
        for (String product : CANDY_PRODUCTS) {
            if (!map.containsKey(product)) {
                map.put(product, Arrays.asList("M&M's", "Reese's", "Snickers", "Milk", "Cookies", "Ice Cream", "Soda",
                        "Chips", "Bananas", "Orange Juice"));
            }
        }
        return map;
    }

    public SimpleSyntheticDataService(
            TransactionRepository transactionRepository,
            TransactionItemRepository transactionItemRepository,
            BasketAnalysisRepository basketAnalysisRepository,
            com.julian.publixai.repository.DiscountEffectivenessRepository discountEffectivenessRepository,
            com.julian.publixai.repository.DiscountRepository discountRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.basketAnalysisRepository = basketAnalysisRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.discountRepository = discountRepository;
    }

    /**
     * Generate exactly 3,000 synthetic transactions and frequently bought together items.
     * This is a one-time setup that creates reliable, simple synthetic data.
     * Reduced from 10K to 3K to speed up ML training.
     * 
     * Process:
     * 1. Check if data already exists - if so, skip generation (prevents infinite loops)
     * 2. Delete all existing transactions and basket analysis (only if regenerating)
     * 3. Generate exactly 3,000 transactions with diverse product combinations
     * 4. Create synthetic frequently bought together items directly (no complex calculation)
     * 
     * @param forceRegenerate If true, deletes existing data and regenerates. If false, only generates if no data exists.
     */
    @Transactional
    public void generateSimpleSyntheticData(boolean forceRegenerate) {
        // Check if data already exists
        long existingTransactions = transactionRepository.count();
        long existingBasketAnalyses = basketAnalysisRepository.count();
        
        // If data exists and we're not forcing regeneration, skip
        if (!forceRegenerate && existingTransactions > 0 && existingBasketAnalyses > 0) {
            log.info("Synthetic data already exists. Skipping generation. Transactions: {}, Basket analyses: {}", 
                    existingTransactions, existingBasketAnalyses);
            return;
        }
        
        log.info("Generating Simple Synthetic Data (3,000 transactions)");

        // Step 1: Delete existing data (only if forcing regeneration or if we're starting fresh)
        if (forceRegenerate || existingTransactions > 0 || existingBasketAnalyses > 0) {
            log.info("Deleting existing transactions and basket analysis...");
            transactionItemRepository.deleteAll();
            transactionRepository.deleteAll();
            basketAnalysisRepository.deleteAll();
        }

        // Step 2: Generate exactly 3,000 transactions
        log.info("Generating 3,000 transactions...");
        List<Transaction> transactions = new ArrayList<>();
        Random random = new Random();
        LocalDate baseDate = LocalDate.of(2024, 10, 1);

        for (int i = 0; i < 3000; i++) {
            Transaction transaction = new Transaction();
            transaction.setTransactionDate(baseDate.plusDays(i % 31)); // Spread across October
            transaction.setTransactionTime(LocalTime.of(
                    random.nextInt(14) + 8, // 8 AM to 9 PM
                    random.nextInt(60)));

            // Pick a random primary candy product
            String primaryProduct = CANDY_PRODUCTS.get(random.nextInt(CANDY_PRODUCTS.size()));

            // Generate transaction items
            List<TransactionItem> items = generateTransactionItems(primaryProduct, random);
            transaction.setItems(items);
            items.forEach(item -> item.setTransaction(transaction));

            transactions.add(transaction);

            // Save in batches of 500 to avoid memory issues
            if (transactions.size() >= 500) {
                transactionRepository.saveAll(transactions);
                transactions.clear();
            }
        }

        // Save remaining transactions
        if (!transactions.isEmpty()) {
            transactionRepository.saveAll(transactions);
        }

        log.info("Generated 3,000 transactions");

        // Step 3: Create synthetic frequently bought together items directly
        log.info("Creating synthetic frequently bought together items...");
        createSyntheticBasketAnalysis(random);

        log.info("Simple Synthetic Data Generation Complete. Total transactions: {}, Total basket analyses: {}", 
                transactionRepository.count(), basketAnalysisRepository.count());
    }

    /**
     * Generate simple synthetic data (default: only if data doesn't exist).
     * Convenience method that calls generateSimpleSyntheticData(false).
     */
    @Transactional
    public void generateSimpleSyntheticData() {
        generateSimpleSyntheticData(false);
    }

    /**
     * Generate COMPLETE historical dataset for one-time initialization.
     * This includes:
     * 1. 3,000 transactions (for frequently bought together)
     * 2. ~210 basket analyses (derived from transactions)
     * 3. ~1,500 discount effectiveness records (lightweight, for discount insights)
     * 4. ~300 discount records (for year-by-year display)
     * 
     * Total: ~5,000 records (under 10K limit)
     * 
     * This should ONLY be called by DataInitializer on first startup.
     */
    @Transactional
    public void generateCompleteHistoricalDataset() {
        generateCompleteHistoricalDataset(false);
    }

    @Transactional
    public void generateCompleteHistoricalDataset(boolean forceRegenerate) {
        log.info("Generating COMPLETE historical dataset (transactions + basket analyses + discounts)...");

        if (forceRegenerate) {
            clearSyntheticData();
        } else {
            long existingTransactions = transactionRepository.count();
            long existingDiscounts = discountRepository.count();
            long existingEffectiveness = discountEffectivenessRepository.count();

            if (existingTransactions > 0 || existingDiscounts > 0 || existingEffectiveness > 0) {
                log.info("Synthetic data already exists. Skipping full dataset generation. " +
                        "Transactions: {}, Discounts: {}, Discount effectiveness: {}",
                        existingTransactions, existingDiscounts, existingEffectiveness);
                return;
            }
        }

        // Step 1 & 2: Generate transactions and basket analyses
        generateSimpleSyntheticData(forceRegenerate);
        
        // Step 3 & 4: Generate lightweight discount data
        generateLightweightDiscountData();
        
        log.info("Complete historical dataset generation finished. Current totals → Transactions: {}, Basket analyses: {}, Discounts: {}, Discount effectiveness: {}",
                transactionRepository.count(),
                basketAnalysisRepository.count(),
                discountRepository.count(),
                discountEffectivenessRepository.count());
    }

    /**
     * Generate lightweight discount data (~1,800 total records).
     * This creates minimal but sufficient discount data for insights without overwhelming the ML service.
     * 
     * Strategy:
     * - 21 products from CANDY_PRODUCTS
     * - 5 discount levels (10%, 15%, 20%, 25%, 30%)
     * - ~15-20 data points per product-discount combination
     * - Spread across 2020-2024 (5 recent years only, not full 10 years)
     * 
     * Total: 21 products × 5 discounts × 17 avg points = ~1,785 records
     */
    @Transactional
    public void generateLightweightDiscountData() {
        generateLightweightDiscountData(false);
    }

    @Transactional
    public void generateLightweightDiscountData(boolean forceRegenerate) {
        long existingEffectiveness = discountEffectivenessRepository.count();
        long existingDiscounts = discountRepository.count();

        if (!forceRegenerate && (existingEffectiveness > 0 || existingDiscounts > 0)) {
            log.info("Discount data already exists. Skipping lightweight generation. Effectiveness: {}, Discounts: {}",
                    existingEffectiveness, existingDiscounts);
            return;
        }

        if (forceRegenerate && (existingEffectiveness > 0 || existingDiscounts > 0)) {
            log.info("Force regenerating discount data. Deleting existing records...");
            discountEffectivenessRepository.deleteAll();
            discountRepository.deleteAll();
        }

        log.info("Generating lightweight discount data (~1,800 records)...");
        
        Random random = new Random();
        List<com.julian.publixai.model.DiscountEffectiveness> effectivenessRecords = new ArrayList<>();
        List<com.julian.publixai.model.Discount> discountRecords = new ArrayList<>();
        
        // Load existing records to avoid duplicates (in case of partial failures)
        Set<String> existingEffectivenessKeys = new HashSet<>();
        if (existingEffectiveness > 0) {
            discountEffectivenessRepository.findAll().forEach(de -> {
                String key = de.getProductName() + "|" + de.getDiscountPercent() + "|" + de.getDate();
                existingEffectivenessKeys.add(key);
            });
            log.info("Found {} existing discount effectiveness records. Will skip duplicates.", existingEffectivenessKeys.size());
        }
        
        // Track generated (product, discount, date) combinations to avoid duplicates
        Set<String> generatedKeys = new HashSet<>();
        
        BigDecimal[] discountLevels = {
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(15),
            BigDecimal.valueOf(20),
            BigDecimal.valueOf(25),
            BigDecimal.valueOf(30)
        };
        
        // Use all 10 years (2015-2024) for complete historical discount data
        int[] years = {2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024};
        
        for (String productName : CANDY_PRODUCTS) {
            for (BigDecimal discountPercent : discountLevels) {
                // Generate 15-20 data points per product-discount combination
                int dataPoints = 15 + random.nextInt(6); // 15-20 points
                int attempts = 0;
                int maxAttempts = dataPoints * 3; // Allow some retries for duplicates
                
                for (int i = 0; i < dataPoints && attempts < maxAttempts; attempts++) {
                    // Pick a random year
                    int year = years[random.nextInt(years.length)];
                    
                    // Pick a random day in October
                    int day = 1 + random.nextInt(31);
                    LocalDate date = LocalDate.of(year, 10, day);
                    
                    // Create unique key to check for duplicates
                    String key = productName + "|" + discountPercent + "|" + date;
                    
                    // Skip if we've already generated this combination OR it already exists in DB
                    if (generatedKeys.contains(key) || existingEffectivenessKeys.contains(key)) {
                        continue; // Try again with a different date
                    }
                    
                    // Mark this combination as generated
                    generatedKeys.add(key);
                    
                    // Generate realistic discount effectiveness data
                    // Higher discounts → higher sales lift (with variation)
                    double baseLift = discountPercent.doubleValue() * (0.6 + random.nextDouble() * 0.4); // 0.6-1.0x multiplier
                    double variation = -3 + (random.nextDouble() * 8); // -3% to +5% variation
                    BigDecimal salesLift = BigDecimal.valueOf(baseLift + variation).max(BigDecimal.ZERO);
                    
                    int unitsSold = 20 + random.nextInt(80); // 20-100 units
                    BigDecimal avgPrice = BigDecimal.valueOf(2.50 + random.nextDouble() * 3.00); // $2.50-$5.50
                    BigDecimal revenue = avgPrice.multiply(BigDecimal.valueOf(unitsSold))
                            .multiply(BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100))));
                    
                    com.julian.publixai.model.DiscountEffectiveness effectiveness = new com.julian.publixai.model.DiscountEffectiveness();
                    effectiveness.setProductName(productName);
                    effectiveness.setDiscountPercent(discountPercent);
                    effectiveness.setDate(date);
                    effectiveness.setUnitsSold(unitsSold);
                    effectiveness.setRevenue(revenue);
                    effectiveness.setAvgUnitPrice(avgPrice);
                    effectiveness.setSalesLiftPercent(salesLift);
                    
                    effectivenessRecords.add(effectiveness);
                    i++; // Only increment when we successfully add a record
                }
            }
        }
        
        // Ensure at least one discount effectiveness record per year per product
        // This guarantees year coverage for all years 2015-2024
        Map<String, Set<Integer>> productYearCoverage = new HashMap<>();
        for (com.julian.publixai.model.DiscountEffectiveness de : effectivenessRecords) {
            productYearCoverage.putIfAbsent(de.getProductName(), new HashSet<>());
            productYearCoverage.get(de.getProductName()).add(de.getDate().getYear());
        }
        
        // Backfill missing years for each product
        for (String productName : CANDY_PRODUCTS) {
            Set<Integer> coveredYears = productYearCoverage.getOrDefault(productName, new HashSet<>());
            for (int year : years) {
                if (!coveredYears.contains(year)) {
                    // Generate at least 1 record for this product-year combination
                    // Use a random discount level to ensure variety
                    BigDecimal discountPercent = discountLevels[random.nextInt(discountLevels.length)];
                    
                    // Pick a random day in October
                    int day = 1 + random.nextInt(31);
                    LocalDate date = LocalDate.of(year, 10, day);
                    String key = productName + "|" + discountPercent + "|" + date;
                    
                    // Retry if duplicate (try different day)
                    int retryAttempts = 0;
                    while ((generatedKeys.contains(key) || existingEffectivenessKeys.contains(key)) && retryAttempts < 10) {
                        day = 1 + random.nextInt(31);
                        date = LocalDate.of(year, 10, day);
                        key = productName + "|" + discountPercent + "|" + date;
                        retryAttempts++;
                    }
                    
                    // Skip if still duplicate after retries
                    if (generatedKeys.contains(key) || existingEffectivenessKeys.contains(key)) {
                        log.warn("Could not generate unique discount effectiveness record for {} in year {} after retries", productName, year);
                        continue;
                    }
                    
                    generatedKeys.add(key);
                    
                    // Generate discount effectiveness data
                    double baseLift = discountPercent.doubleValue() * (0.6 + random.nextDouble() * 0.4);
                    double variation = -3 + (random.nextDouble() * 8);
                    BigDecimal salesLift = BigDecimal.valueOf(baseLift + variation).max(BigDecimal.ZERO);
                    
                    int unitsSold = 20 + random.nextInt(80);
                    BigDecimal avgPrice = BigDecimal.valueOf(2.50 + random.nextDouble() * 3.00);
                    BigDecimal revenue = avgPrice.multiply(BigDecimal.valueOf(unitsSold))
                            .multiply(BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100))));
                    
                    com.julian.publixai.model.DiscountEffectiveness effectiveness = new com.julian.publixai.model.DiscountEffectiveness();
                    effectiveness.setProductName(productName);
                    effectiveness.setDiscountPercent(discountPercent);
                    effectiveness.setDate(date);
                    effectiveness.setUnitsSold(unitsSold);
                    effectiveness.setRevenue(revenue);
                    effectiveness.setAvgUnitPrice(avgPrice);
                    effectiveness.setSalesLiftPercent(salesLift);
                    
                    effectivenessRecords.add(effectiveness);
                    log.debug("Backfilled discount effectiveness record for {} in year {}", productName, year);
                }
            }
        }
        
        // Now generate discount records for year-by-year display
        for (String productName : CANDY_PRODUCTS) {
            for (BigDecimal discountPercent : discountLevels) {
                // Create discount records per product-discount combination for year-by-year display
                for (int year : years) {
                    // 60% chance of having a discount for this product-discount-year combination
                    if (random.nextDouble() < 0.6) {
                        // Pick a random week in October
                        int startDay = 1 + random.nextInt(25); // Days 1-25 (to allow multi-day discounts)
                        int duration = 3 + random.nextInt(5); // 3-7 days
                        
                        LocalDate startDate = LocalDate.of(year, 10, startDay);
                        LocalDate endDate = startDate.plusDays(duration - 1);
                        
                        // Ensure end date doesn't exceed October 31
                        if (endDate.getDayOfMonth() > 31) {
                            endDate = LocalDate.of(year, 10, 31);
                        }
                        
                        com.julian.publixai.model.Discount discount = new com.julian.publixai.model.Discount();
                        discount.setProductName(productName);
                        discount.setDiscountPercent(discountPercent);
                        discount.setStartDate(startDate);
                        discount.setEndDate(endDate);
                        discount.setDescription("Halloween promotion - " + discountPercent + "% off");
                        
                        discountRecords.add(discount);
                    }
                }
            }
        }
        
        // Save all discount effectiveness data
        if (!effectivenessRecords.isEmpty()) {
            discountEffectivenessRepository.saveAll(effectivenessRecords);
            log.info("Saved {} discount effectiveness records", effectivenessRecords.size());
        }
        
        // Save all discount records
        if (!discountRecords.isEmpty()) {
            discountRepository.saveAll(discountRecords);
            log.info("Saved {} discount records for year-by-year display", discountRecords.size());
        }
        
        log.info("Lightweight discount data generation complete. Total: {} effectiveness + {} discounts = {} records",
                effectivenessRecords.size(), discountRecords.size(), effectivenessRecords.size() + discountRecords.size());
    }

    private void clearSyntheticData() {
        log.info("Clearing existing synthetic data (transactions, basket analyses, discounts)...");
        transactionItemRepository.deleteAll();
        transactionRepository.deleteAll();
        basketAnalysisRepository.deleteAll();
        discountEffectivenessRepository.deleteAll();
        discountRepository.deleteAll();
    }

    /**
     * Generate transaction items for a primary product.
     * Always includes the primary product and 5-8 associated items.
     */
    private List<TransactionItem> generateTransactionItems(String primaryProduct, Random random) {
        List<TransactionItem> items = new ArrayList<>();
        Set<String> productsInTransaction = new java.util.HashSet<>();

        // Always include primary product
        productsInTransaction.add(primaryProduct);

        // Add associated items (5-8 items)
        if (ASSOCIATIONS.containsKey(primaryProduct)) {
            List<String> associatedItems = ASSOCIATIONS.get(primaryProduct);
            int numAssociations = 5 + random.nextInt(4); // 5-8 items
            for (int i = 0; i < numAssociations && i < associatedItems.size(); i++) {
                productsInTransaction.add(associatedItems.get(i));
            }
        } else {
            // For products without predefined associations, add random items
            List<String> allItems = new ArrayList<>(CANDY_PRODUCTS);
            allItems.addAll(GROCERY_ITEMS);
            int numAssociations = 5 + random.nextInt(4);
            for (int i = 0; i < numAssociations && !allItems.isEmpty(); i++) {
                String randomItem = allItems.get(random.nextInt(allItems.size()));
                if (!productsInTransaction.contains(randomItem)) {
                    productsInTransaction.add(randomItem);
                }
            }
        }

        // Create transaction items
        for (String productName : productsInTransaction) {
            TransactionItem item = new TransactionItem();
            item.setProductName(productName);
            item.setQuantity(random.nextInt(3) + 1); // 1-3 units
            item.setUnitPrice(BigDecimal.valueOf(2.50 + random.nextDouble() * 3.0)); // $2.50 - $5.50
            item.setDiscountPercent(BigDecimal.ZERO);
            items.add(item);
        }

        return items;
    }

    /**
     * Create synthetic frequently bought together items directly.
     * Uses predefined associations and generates realistic co-occurrence counts.
     */
    private void createSyntheticBasketAnalysis(Random random) {
        List<BasketAnalysis> analyses = new ArrayList<>();

        log.debug("Creating synthetic basket analysis for {} products", CANDY_PRODUCTS.size());
        for (String primaryProduct : CANDY_PRODUCTS) {
            log.debug("Processing basket analysis for product: '{}'", primaryProduct);
            if (ASSOCIATIONS.containsKey(primaryProduct)) {
                List<String> associatedItems = ASSOCIATIONS.get(primaryProduct);

                // Generate top 10 associations with realistic co-occurrence counts
                for (int i = 0; i < Math.min(10, associatedItems.size()); i++) {
                    String associatedProduct = associatedItems.get(i);

                    // Generate realistic co-occurrence count (higher for top items)
                    // Top item: 200-300, 2nd: 150-250, 3rd: 100-200, etc.
                    int baseCount = 200 - (i * 20);
                    int coOccurrenceCount = baseCount + random.nextInt(100);

                    // Calculate confidence and support
                    BigDecimal confidence = BigDecimal.valueOf(coOccurrenceCount)
                            .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
                    BigDecimal support = BigDecimal.valueOf(coOccurrenceCount)
                            .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);

                    BasketAnalysis analysis = new BasketAnalysis();
                    analysis.setPrimaryProduct(primaryProduct);
                    analysis.setAssociatedProduct(associatedProduct);
                    analysis.setCoOccurrenceCount(coOccurrenceCount);
                    analysis.setConfidenceScore(confidence);
                    analysis.setSupportScore(support);

                    analyses.add(analysis);
                }
            } else {
                // For products without predefined associations, create generic associations
                List<String> commonItems = Arrays.asList("M&M's", "Reese's", "Snickers", "Milk", "Cookies", "Ice Cream",
                        "Soda", "Chips", "Bananas", "Orange Juice");
                for (int i = 0; i < 10; i++) {
                    String associatedProduct = commonItems.get(i);
                    int coOccurrenceCount = 100 + random.nextInt(100);

                    BigDecimal confidence = BigDecimal.valueOf(coOccurrenceCount)
                            .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
                    BigDecimal support = BigDecimal.valueOf(coOccurrenceCount)
                            .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);

                    BasketAnalysis analysis = new BasketAnalysis();
                    analysis.setPrimaryProduct(primaryProduct);
                    analysis.setAssociatedProduct(associatedProduct);
                    analysis.setCoOccurrenceCount(coOccurrenceCount);
                    analysis.setConfidenceScore(confidence);
                    analysis.setSupportScore(support);

                    analyses.add(analysis);
                }
            }
        }

        // Save all basket analyses
        basketAnalysisRepository.saveAll(analyses);
        log.info("Created {} frequently bought together associations", analyses.size());
    }
}

