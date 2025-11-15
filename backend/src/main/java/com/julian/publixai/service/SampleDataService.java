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

import com.julian.publixai.model.Discount;
import com.julian.publixai.model.DiscountEffectiveness;
import com.julian.publixai.model.Transaction;
import com.julian.publixai.model.TransactionItem;
import com.julian.publixai.repository.BasketAnalysisRepository;
import com.julian.publixai.repository.DiscountEffectivenessRepository;
import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.repository.SaleRepository;
import com.julian.publixai.repository.TransactionItemRepository;
import com.julian.publixai.repository.TransactionRepository;

/**
 * SampleDataService
 * 
 * Purpose: Generate random sample transaction data for basket analysis.
 * Creates realistic shopping patterns with frequently bought together items.
 */
@Service
public class SampleDataService {

    private static final Logger log = LoggerFactory.getLogger(SampleDataService.class);

    private final BasketAnalysisService basketAnalysisService;
    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;
    private final BasketAnalysisRepository basketAnalysisRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final DiscountRepository discountRepository;
    private final SaleRepository saleRepository;

    // Common candy products (matching CSV product names)
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
    // Using actual product names from CSV
    private static final Map<String, List<String>> ASSOCIATIONS = createAssociationsMap();

    private static Map<String, List<String>> createAssociationsMap() {
        Map<String, List<String>> map = new HashMap<>();
        // Ensure each product has at least 10 associations for top 10 display
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
        return map;
    }

    public SampleDataService(BasketAnalysisService basketAnalysisService,
            TransactionRepository transactionRepository,
            TransactionItemRepository transactionItemRepository,
            BasketAnalysisRepository basketAnalysisRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            DiscountRepository discountRepository,
            SaleRepository saleRepository) {
        this.basketAnalysisService = basketAnalysisService;
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
        this.basketAnalysisRepository = basketAnalysisRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.discountRepository = discountRepository;
        this.saleRepository = saleRepository;
    }

    /**
     * Count total transactions in database.
     * Used to check if historical dataset exists.
     */
    @Transactional(readOnly = true)
    public long countTransactions() {
        return transactionRepository.count();
    }

    /**
     * Delete all transactions, transaction items, and basket analysis data.
     * Used to reset the database before generating fresh historical data.
     * Deletes in order to respect foreign key constraints.
     */
    @Transactional
    public void deleteAllTransactionsAndBasketAnalysis() {
        // Delete in order to respect foreign key constraints
        transactionItemRepository.deleteAll();
        transactionRepository.deleteAll();
        basketAnalysisRepository.deleteAll();
    }

    /**
     * Save a batch of transactions in a separate transaction.
     * This prevents optimistic locking issues when saving large datasets.
     * Each batch is saved independently to avoid persistence context conflicts.
     */
    @Transactional
    public void saveTransactionBatch(List<Transaction> batch) {
        // Save transactions - cascade will automatically save items
        // Use a new ArrayList to avoid potential issues with subList
        @SuppressWarnings("null")
        List<Transaction> safeBatch = new ArrayList<>(batch);
        transactionRepository.saveAll(safeBatch);
        // Flush to ensure data is persisted before next batch
        transactionRepository.flush();
    }

    /**
     * Generate comprehensive historical transaction dataset (ONE TIME ONLY).
     * Creates a large dataset covering 2015-2024 October periods for all products.
     * This is the baseline historical dataset used to derive frequently bought
     * together items.
     * 
     * This should only be called once during initial setup.
     * 
     * NOTE: Not @Transactional to avoid optimistic locking issues.
     * Each batch save and year processing uses its own transaction boundary.
     */
    public void generateHistoricalTransactionDataset() {
        log.info("Generating ONE-TIME historical transaction dataset (2015-2024)");

        LocalDate overallStart = LocalDate.of(2015, 10, 1);
        LocalDate overallEnd = LocalDate.of(2024, 10, 31);

        // Generate for each October from 2015-2024 (10 years)
        for (int year = 2015; year <= 2024; year++) {
            LocalDate octStart = LocalDate.of(year, 10, 1);
            LocalDate octEnd = LocalDate.of(year, 10, 31);

            // Generate 50 transactions per day for faster generation while maintaining data quality
            // This ensures we have enough data to generate top 10 associations for ALL products
            // Reduced from 500 to 50 to avoid thread starvation and improve performance
            log.info("Generating transactions for October {}...", year);
            generateSampleTransactions(octStart, octEnd, 50);

            // Flush periodically to avoid memory issues with large datasets
            if (year % 2 == 0) {
                transactionRepository.flush();
            }
        }

        // Generate discount effectiveness data for the entire range (2015-2024)
        log.info("Generating discount effectiveness data for 2015-2024...");
        generateDiscountEffectivenessData(overallStart, overallEnd);

        // Recalculate basket analysis ONCE for the entire historical range
        // This processes ALL ~15,500 transactions together for accurate co-occurrence counts
        log.info("Recalculating basket analysis for entire historical range (2015-2024)...");
        basketAnalysisService.recalculateBasketAnalysis(overallStart, overallEnd);

        log.info("Historical transaction dataset generation complete. Total transactions: {}, Total basket analyses: {}", 
                transactionRepository.count(), basketAnalysisRepository.count());
    }

    /**
     * Generate sample transactions for a date range.
     * Ensures ALL candy products from sales table get transaction data with diverse
     * associations.
     * 
     * Used for:
     * 1. Historical baseline dataset (2015-2024) - ONE TIME
     * 2. New product predictions - generates predicted associations for new
     * products
     * 
     * NOTE: This method is NOT @Transactional to avoid optimistic locking issues.
     * The caller (generateHistoricalTransactionDataset) manages transactions.
     */
    public void generateSampleTransactions(LocalDate startDate, LocalDate endDate, int transactionsPerDay) {
        List<Transaction> transactions = new ArrayList<>();
        Random random = new Random();

        // Get all unique product names from sales table to ensure all products get data
        List<String> allProducts = saleRepository.findDistinctProductNames();
        if (allProducts.isEmpty()) {
            // Fallback to CANDY_PRODUCTS if sales table is empty
            allProducts = new ArrayList<>(CANDY_PRODUCTS);
        }

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            // Calculate guaranteed transactions per product
            // Ensure we don't exceed transactionsPerDay total
            int guaranteedPerProduct = Math.max(1, transactionsPerDay / Math.max(1, allProducts.size()));
            int totalGuaranteed = guaranteedPerProduct * allProducts.size();
            
            // If guaranteed transactions exceed the limit, cap it
            if (totalGuaranteed > transactionsPerDay) {
                guaranteedPerProduct = Math.max(1, transactionsPerDay / allProducts.size());
            }

            // Generate guaranteed transactions for each product
            for (String product : allProducts) {
                for (int i = 0; i < guaranteedPerProduct; i++) {
                    Transaction transaction = new Transaction();
                    transaction.setTransactionDate(currentDate);
                    transaction.setTransactionTime(LocalTime.of(
                            random.nextInt(14) + 8, // 8 AM to 9 PM
                            random.nextInt(60)));

                    // Generate items for this transaction with the specific product as primary
                    List<TransactionItem> items = generateTransactionItemsForProduct(random, product, allProducts);
                    transaction.setItems(items);

                    // Set bidirectional relationship
                    items.forEach(item -> item.setTransaction(transaction));

                    transactions.add(transaction);
                }
            }

            // Add remaining random transactions for variety (if any)
            int remainingTransactions = transactionsPerDay - (guaranteedPerProduct * allProducts.size());
            for (int i = 0; i < remainingTransactions && remainingTransactions > 0; i++) {
                Transaction transaction = new Transaction();
                transaction.setTransactionDate(currentDate);
                transaction.setTransactionTime(LocalTime.of(
                        random.nextInt(14) + 8,
                        random.nextInt(60)));

                List<TransactionItem> items = generateTransactionItems(random, allProducts);
                transaction.setItems(items);
                items.forEach(item -> item.setTransaction(transaction));
                transactions.add(transaction);
            }

            currentDate = currentDate.plusDays(1);
        }

        // Save transactions in batches to avoid memory issues and optimistic locking
        // conflicts
        // Use smaller batches and save each in its own transaction
        int batchSize = 500; // Reduced batch size to avoid persistence context issues
        for (int i = 0; i < transactions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, transactions.size());
            @SuppressWarnings("null")
            List<Transaction> batch = new ArrayList<>(transactions.subList(i, end));

            // Save batch in its own transaction to avoid optimistic locking conflicts
            saveTransactionBatch(batch);

            if (i % 5000 == 0 && transactions.size() > 5000) {
                log.debug("Saved {} of {} transactions...", end, transactions.size());
            }
        }

        // Note: Don't recalculate basket analysis here per year
        // It will be done ONCE after ALL years (2015-2024) are generated in
        // generateHistoricalTransactionDataset()
        // This ensures we process the complete dataset and avoid partial calculations
    }

    /**
     * Generate discount effectiveness data from transaction items with discounts.
     * Creates comprehensive discount analysis data for all products.
     */
    private void generateDiscountEffectivenessData(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = transactionRepository.findByTransactionDateBetween(startDate, endDate);
        List<DiscountEffectiveness> effectivenessRecords = new ArrayList<>();
        Random random = new Random();

        log.info("Generating discount effectiveness data from {} transactions", transactions.size());

        // Group transaction items by product, date, and discount percent
        // This creates rich discount effectiveness data across all discount levels
        Map<String, Map<LocalDate, Map<BigDecimal, List<TransactionItem>>>> grouped = new HashMap<>();
        int itemsWithDiscounts = 0;

        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getTransactionDate();
            for (TransactionItem item : transaction.getItems()) {
                if (item.getDiscountPercent() != null && item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                    String productName = item.getProductName();
                    BigDecimal discountPercent = item.getDiscountPercent();
                    itemsWithDiscounts++;

                    grouped.putIfAbsent(productName, new HashMap<>());
                    grouped.get(productName).putIfAbsent(date, new HashMap<>());
                    grouped.get(productName).get(date).putIfAbsent(discountPercent, new ArrayList<>());
                    grouped.get(productName).get(date).get(discountPercent).add(item);
                }
            }
        }

        log.debug("Found {} transaction items with discounts, grouped into {} products", itemsWithDiscounts, grouped.size());

        // Create discount effectiveness records
        for (Map.Entry<String, Map<LocalDate, Map<BigDecimal, List<TransactionItem>>>> productEntry : grouped
                .entrySet()) {
            String productName = productEntry.getKey();

            for (Map.Entry<LocalDate, Map<BigDecimal, List<TransactionItem>>> dateEntry : productEntry.getValue()
                    .entrySet()) {
                LocalDate date = dateEntry.getKey();

                for (Map.Entry<BigDecimal, List<TransactionItem>> discountEntry : dateEntry.getValue().entrySet()) {
                    BigDecimal discountPercent = discountEntry.getKey();
                    List<TransactionItem> items = discountEntry.getValue();

                    // Calculate totals
                    int totalUnits = items.stream().mapToInt(TransactionItem::getQuantity).sum();
                    BigDecimal totalRevenue = items.stream()
                            .map(item -> item.getUnitPrice()
                                    .multiply(BigDecimal.ONE.subtract(
                                            discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                                    .multiply(BigDecimal.valueOf(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal avgUnitPrice = items.stream()
                            .map(TransactionItem::getUnitPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);

                    // Calculate sales lift with realistic variation
                    // Higher discounts = higher lift, with some randomness for realism
                    double baseLiftMultiplier = 0.5 + (random.nextDouble() * 0.3); // 0.5-0.8 multiplier
                    BigDecimal salesLift = discountPercent.multiply(BigDecimal.valueOf(baseLiftMultiplier));
                    // Add some variation: -5% to +10% of calculated lift
                    double variation = -5 + (random.nextDouble() * 15);
                    salesLift = salesLift.add(BigDecimal.valueOf(variation));
                    if (salesLift.compareTo(BigDecimal.ZERO) < 0) {
                        salesLift = BigDecimal.ZERO; // Ensure non-negative
                    }

                    DiscountEffectiveness effectiveness = new DiscountEffectiveness();
                    effectiveness.setProductName(productName);
                    effectiveness.setDiscountPercent(discountPercent);
                    effectiveness.setDate(date);
                    effectiveness.setUnitsSold(totalUnits);
                    effectiveness.setRevenue(totalRevenue);
                    effectiveness.setAvgUnitPrice(avgUnitPrice);
                    effectiveness.setSalesLiftPercent(salesLift);

                    effectivenessRecords.add(effectiveness);
                }
            }
        }

        log.info("Generated {} discount effectiveness records", effectivenessRecords.size());

        if (!effectivenessRecords.isEmpty()) {
            discountEffectivenessRepository.saveAll(effectivenessRecords);
            log.info("Saved {} discount effectiveness records to database", effectivenessRecords.size());
        } else {
            // Fallback: Generate synthetic discount data if no transaction-based data
            // exists
            log.warn("No discount effectiveness data from transactions. Generating synthetic data...");
            generateSyntheticDiscountData(startDate, endDate);
        }
    }

    /**
     * Generate synthetic discount effectiveness data as a fallback.
     * Creates comprehensive discount data for all products with various discount
     * levels.
     * Also creates Discount records for year-by-year display.
     */
    private void generateSyntheticDiscountData(LocalDate startDate, LocalDate endDate) {
        List<String> allProducts = saleRepository.findDistinctProductNames();
        if (allProducts.isEmpty()) {
            allProducts = new ArrayList<>(CANDY_PRODUCTS);
        }

        List<DiscountEffectiveness> syntheticRecords = new ArrayList<>();
        List<Discount> discountRecords = new ArrayList<>();
        Random random = new Random();
        BigDecimal[] discountLevels = {
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(30)
        };

        // Generate data for each product, each discount level, across the date range
        // Make discounts more realistic: not every product gets discounts every day
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            final LocalDate dateForLambda = currentDate; // Create final copy for lambda
            for (String productName : allProducts) {
                // Realistic pattern: 30% chance of any discount on a given day
                // Popular products (first 5 in list) get discounts more often (50% chance)
                boolean isPopular = allProducts.indexOf(productName) < 5;
                double discountChance = isPopular ? 0.5 : 0.3;

                if (random.nextDouble() > discountChance) {
                    continue; // No discount for this product on this day
                }

                // If discount occurs, only 1 discount level per day (more realistic)
                BigDecimal discountPercent = discountLevels[random.nextInt(discountLevels.length)];

                // Weight discounts: 10% and 15% more common, 30% less common
                double discountRoll = random.nextDouble();
                if (discountRoll < 0.4) {
                    discountPercent = BigDecimal.valueOf(10); // 40% chance
                } else if (discountRoll < 0.7) {
                    discountPercent = BigDecimal.valueOf(15); // 30% chance
                } else if (discountRoll < 0.85) {
                    discountPercent = BigDecimal.valueOf(20); // 15% chance
                } else if (discountRoll < 0.95) {
                    discountPercent = BigDecimal.valueOf(25); // 10% chance
                } else {
                    discountPercent = BigDecimal.valueOf(30); // 5% chance (rare)
                }

                // Create Discount record for year-by-year display (3-7 day duration)
                // Create one discount per product per day
                boolean discountExistsForDate = discountRecords.stream()
                        .anyMatch(d -> d.getProductName().equals(productName) &&
                                d.getStartDate().equals(dateForLambda));

                if (!discountExistsForDate) {
                    int duration = 3 + random.nextInt(5); // 3-7 days
                    LocalDate discountEndDate = dateForLambda.plusDays(duration - 1);

                    Discount discount = new Discount();
                    discount.setProductName(productName);
                    discount.setDiscountPercent(discountPercent);
                    discount.setStartDate(dateForLambda);
                    discount.setEndDate(discountEndDate);
                    discount.setDescription("Halloween promotion - " + discountPercent + "% off");
                    discountRecords.add(discount);
                }

                // Generate realistic units sold (varies by discount level)
                int baseUnits = 50 + random.nextInt(100); // 50-150 base units
                int unitsSold = (int) (baseUnits * (1.0 + (discountPercent.doubleValue() / 100.0) * 0.5)); // Higher
                                                                                                           // discount =
                                                                                                           // more units

                // Calculate revenue
                BigDecimal avgUnitPrice = BigDecimal.valueOf(2.50 + random.nextDouble() * 3.0); // $2.50-$5.50
                BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                        discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                BigDecimal revenue = avgUnitPrice.multiply(discountMultiplier)
                        .multiply(BigDecimal.valueOf(unitsSold));

                // Calculate sales lift (higher discounts = higher lift)
                BigDecimal salesLift = discountPercent.multiply(BigDecimal.valueOf(0.4 + random.nextDouble() * 0.3))
                        .add(BigDecimal.valueOf(random.nextDouble() * 5 - 2.5)); // Add variation
                if (salesLift.compareTo(BigDecimal.ZERO) < 0) {
                    salesLift = BigDecimal.ZERO;
                }

                DiscountEffectiveness effectiveness = new DiscountEffectiveness();
                effectiveness.setProductName(productName);
                effectiveness.setDiscountPercent(discountPercent);
                effectiveness.setDate(dateForLambda);
                effectiveness.setUnitsSold(unitsSold);
                effectiveness.setRevenue(revenue);
                effectiveness.setAvgUnitPrice(avgUnitPrice);
                effectiveness.setSalesLiftPercent(salesLift);

                syntheticRecords.add(effectiveness);
            }
            currentDate = currentDate.plusDays(1);
        }

        // Save discount records first (these are needed for year-by-year display)
        if (!discountRecords.isEmpty()) {
            // Check database for existing discounts to avoid duplicates
            List<Discount> existingDiscounts = discountRepository.findAll();
            Set<String> existingKeys = existingDiscounts.stream()
                    .map(d -> d.getProductName() + "|" + d.getStartDate() + "|" + d.getDiscountPercent())
                    .collect(java.util.stream.Collectors.toSet());

            List<Discount> newDiscounts = discountRecords.stream()
                    .filter(d -> {
                        String key = d.getProductName() + "|" + d.getStartDate() + "|" + d.getDiscountPercent();
                        return !existingKeys.contains(key);
                    })
                    .collect(java.util.stream.Collectors.toList());

            if (!newDiscounts.isEmpty()) {
                discountRepository.saveAll(newDiscounts);
                log.info("Generated and saved {} new synthetic discount records (total in DB: {})", 
                        newDiscounts.size(), existingDiscounts.size() + newDiscounts.size());
            } else {
                log.debug("All {} discount records already exist in database", discountRecords.size());
            }
        }

        // Save discount effectiveness records
        if (!syntheticRecords.isEmpty()) {
            discountEffectivenessRepository.saveAll(syntheticRecords);
            log.info("Generated and saved {} synthetic discount effectiveness records", syntheticRecords.size());
        }
    }

    /**
     * Generate synthetic discount data directly (public method for controller use).
     * Creates comprehensive discount data for all products.
     */
    @Transactional
    public void generateSyntheticDiscountDataDirectly(LocalDate startDate, LocalDate endDate) {
        generateSyntheticDiscountData(startDate, endDate);
    }

    /**
     * Generate synthetic discount data for a specific product.
     * Creates comprehensive discount effectiveness records for a single product.
     */
    @Transactional
    public void generateSyntheticDiscountDataForProduct(String productName, LocalDate startDate, LocalDate endDate) {
        List<DiscountEffectiveness> syntheticRecords = new ArrayList<>();
        Random random = new Random();
        BigDecimal[] discountLevels = {
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(30)
        };

        // Generate data for this product across the date range
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Each product gets 2-3 discount levels per day for comprehensive data
            int numDiscounts = random.nextInt(2) + 2; // 2-3 discounts per day
            Set<BigDecimal> usedDiscounts = new HashSet<>();

            for (int i = 0; i < numDiscounts; i++) {
                BigDecimal discountPercent = discountLevels[random.nextInt(discountLevels.length)];

                // Avoid duplicate discount levels for same product/date
                if (usedDiscounts.contains(discountPercent)) {
                    continue;
                }
                usedDiscounts.add(discountPercent);

                // Generate realistic units sold (varies by discount level)
                int baseUnits = 50 + random.nextInt(100); // 50-150 base units
                int unitsSold = (int) (baseUnits * (1.0 + (discountPercent.doubleValue() / 100.0) * 0.5)); // Higher
                                                                                                           // discount =
                                                                                                           // more units

                // Calculate revenue
                BigDecimal avgUnitPrice = BigDecimal.valueOf(2.50 + random.nextDouble() * 3.0); // $2.50-$5.50
                BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                        discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                BigDecimal revenue = avgUnitPrice.multiply(discountMultiplier)
                        .multiply(BigDecimal.valueOf(unitsSold));

                // Calculate sales lift (higher discounts = higher lift)
                BigDecimal salesLift = discountPercent.multiply(BigDecimal.valueOf(0.4 + random.nextDouble() * 0.3))
                        .add(BigDecimal.valueOf(random.nextDouble() * 5 - 2.5)); // Add variation
                if (salesLift.compareTo(BigDecimal.ZERO) < 0) {
                    salesLift = BigDecimal.ZERO;
                }

                DiscountEffectiveness effectiveness = new DiscountEffectiveness();
                effectiveness.setProductName(productName);
                effectiveness.setDiscountPercent(discountPercent);
                effectiveness.setDate(currentDate);
                effectiveness.setUnitsSold(unitsSold);
                effectiveness.setRevenue(revenue);
                effectiveness.setAvgUnitPrice(avgUnitPrice);
                effectiveness.setSalesLiftPercent(salesLift);

                syntheticRecords.add(effectiveness);
            }
            currentDate = currentDate.plusDays(1);
        }

        if (!syntheticRecords.isEmpty()) {
            discountEffectivenessRepository.saveAll(syntheticRecords);
            log.info("Generated and saved {} synthetic discount effectiveness records for {}", 
                    syntheticRecords.size(), productName);
        }
    }

    /**
     * Generate transaction items for a specific primary product.
     * Ensures the product gets diverse associations.
     */
    private List<TransactionItem> generateTransactionItemsForProduct(Random random, String primaryProduct,
            List<String> availableProducts) {
        List<TransactionItem> items = new ArrayList<>();
        Set<String> productsInTransaction = new HashSet<>();

        // Always include the primary product
        productsInTransaction.add(primaryProduct);

        // Add associated items based on predefined associations
        if (ASSOCIATIONS.containsKey(primaryProduct)) {
            List<String> associatedItems = ASSOCIATIONS.get(primaryProduct);
            // Always add ALL associated items to ensure maximum co-occurrences
            // This creates richer transaction data and ensures we have 10+ associations per
            // product
            productsInTransaction.addAll(associatedItems);
        } else {
            // For products without predefined associations, add diverse items
            // Add 8-10 random items from available products and grocery items for richer
            // data
            // This ensures we have enough co-occurrences to build top 10 lists
            int numAssociations = 8 + random.nextInt(3); // 8-10 items
            List<String> allAvailable = new ArrayList<>(availableProducts);
            allAvailable.addAll(GROCERY_ITEMS);
            Set<String> added = new HashSet<>();
            added.add(primaryProduct);

            for (int i = 0; i < numAssociations && !allAvailable.isEmpty(); i++) {
                String randomItem = allAvailable.get(random.nextInt(allAvailable.size()));
                if (!added.contains(randomItem)) {
                    productsInTransaction.add(randomItem);
                    added.add(randomItem);
                }
            }
        }

        // Create transaction items
        for (String productName : productsInTransaction) {
            TransactionItem item = new TransactionItem();
            item.setProductName(productName);
            item.setQuantity(random.nextInt(3) + 1); // 1-3 units
            item.setUnitPrice(BigDecimal.valueOf(2.50 + random.nextDouble() * 5.0)); // $2.50 - $7.50

            // 60% chance of discount to generate comprehensive discount effectiveness data
            if (random.nextDouble() < 0.6) {
                BigDecimal[] discountOptions = {
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(15),
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(25),
                        BigDecimal.valueOf(30)
                };
                item.setDiscountPercent(discountOptions[random.nextInt(discountOptions.length)]);
            } else {
                item.setDiscountPercent(BigDecimal.ZERO);
            }

            items.add(item);
        }

        return items;
    }

    private List<TransactionItem> generateTransactionItems(Random random, List<String> availableProducts) {
        List<TransactionItem> items = new ArrayList<>();
        Set<String> productsInTransaction = new HashSet<>();

        // Pick a primary candy (70% chance)
        if (random.nextDouble() < 0.7 && !availableProducts.isEmpty()) {
            String primaryCandy = availableProducts.get(random.nextInt(availableProducts.size()));
            productsInTransaction.add(primaryCandy);

            // Add associated items based on predefined associations
            if (ASSOCIATIONS.containsKey(primaryCandy)) {
                List<String> associatedItems = ASSOCIATIONS.get(primaryCandy);
                // 85% chance to add each associated item to ensure diverse, rich associations
                for (String item : associatedItems) {
                    if (random.nextDouble() < 0.85) {
                        productsInTransaction.add(item);
                    }
                }
            } else {
                // For products without associations, add 4-7 random items for richer data
                int numItems = 4 + random.nextInt(4);
                List<String> allAvailable = new ArrayList<>(availableProducts);
                allAvailable.addAll(GROCERY_ITEMS);
                for (int i = 0; i < numItems && !allAvailable.isEmpty(); i++) {
                    String randomItem = allAvailable.get(random.nextInt(allAvailable.size()));
                    if (!randomItem.equals(primaryCandy)) {
                        productsInTransaction.add(randomItem);
                    }
                }
            }
        } else {
            // 30% chance: just grocery items, no candy
            int numItems = random.nextInt(3) + 1;
            for (int i = 0; i < numItems; i++) {
                productsInTransaction.add(GROCERY_ITEMS.get(random.nextInt(GROCERY_ITEMS.size())));
            }
        }

        // Create transaction items
        for (String productName : productsInTransaction) {
            TransactionItem item = new TransactionItem();
            item.setProductName(productName);
            item.setQuantity(random.nextInt(3) + 1); // 1-3 units
            item.setUnitPrice(BigDecimal.valueOf(2.50 + random.nextDouble() * 5.0)); // $2.50 - $7.50

            // 60% chance of discount to generate comprehensive discount effectiveness data
            if (random.nextDouble() < 0.6) {
                BigDecimal[] discountOptions = {
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(15),
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(25),
                        BigDecimal.valueOf(30)
                };
                item.setDiscountPercent(discountOptions[random.nextInt(discountOptions.length)]);
            } else {
                item.setDiscountPercent(BigDecimal.ZERO);
            }

            items.add(item);
        }

        return items;
    }

    /**
     * Generate sample transactions for October 2025 (Halloween season).
     */
    @Transactional
    public void generateOctoberSampleData() {
        LocalDate octStart = LocalDate.of(2025, 10, 1);
        LocalDate octEnd = LocalDate.of(2025, 10, 31);
        generateSampleTransactions(octStart, octEnd, 50); // 50 transactions per day
    }
}
