package com.julian.publixai.service;

import com.julian.publixai.model.*;
import com.julian.publixai.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HistoricalDataGeneratorService
 * 
 * Purpose: Generate synthetic discount and transaction data from historical CSV.
 * Reads oct_sales_2015_2024.csv and creates:
 * - Discount promotions (with realistic patterns)
 * - Transaction data (breaking down daily sales into individual transactions)
 * - Discount effectiveness records
 * - Basket analysis data
 */
@Service
public class HistoricalDataGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDataGeneratorService.class);

    private final SaleRepository saleRepository;
    private final DiscountRepository discountRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final TransactionRepository transactionRepository;
    private final BasketAnalysisService basketAnalysisService;

    // Discount percentages that are commonly used
    private static final BigDecimal[] DISCOUNT_OPTIONS = {
        BigDecimal.valueOf(10),
        BigDecimal.valueOf(15),
        BigDecimal.valueOf(20),
        BigDecimal.valueOf(25),
        BigDecimal.valueOf(30)
    };

    // Products that are frequently bought together (based on common patterns)
    private static final Map<String, List<String>> PRODUCT_ASSOCIATIONS = Map.of(
        "Reese's", Arrays.asList("M&M's", "Snickers", "Hershey's Chocolate", "Milk", "Ice Cream"),
        "M&M's", Arrays.asList("Reese's", "Snickers", "Twix", "Soda", "Chips"),
        "Snickers", Arrays.asList("Reese's", "M&M's", "Twix", "Soda", "Milk"),
        "Twix", Arrays.asList("M&M's", "Snickers", "Milky Way", "Soda"),
        "Hershey's Chocolate", Arrays.asList("Reese's", "M&M's", "Milk", "Ice Cream", "Bananas"),
        "Milky Way", Arrays.asList("Twix", "3 Musketeers", "M&M's", "Soda"),
        "3 Musketeers", Arrays.asList("Milky Way", "Twix", "M&M's", "Soda")
    );

    // Common grocery items
    private static final List<String> GROCERY_ITEMS = Arrays.asList(
        "Milk", "Bread", "Eggs", "Bananas", "Apples", "Orange Juice",
        "Chips", "Soda", "Ice Cream", "Cookies", "Cereal", "Yogurt"
    );

    public HistoricalDataGeneratorService(
            SaleRepository saleRepository,
            DiscountRepository discountRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            TransactionRepository transactionRepository,
            BasketAnalysisService basketAnalysisService) {
        this.saleRepository = saleRepository;
        this.discountRepository = discountRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.transactionRepository = transactionRepository;
        this.basketAnalysisService = basketAnalysisService;
    }

    /**
     * Generate all synthetic data from historical CSV.
     */
    @Transactional
    public void generateFromHistoricalCsv() {
        log.info("Starting historical data generation...");
        
        // Read CSV and parse sales data
        List<SaleRecord> sales = readHistoricalCsv();
        log.info("Read {} sales records from CSV", sales.size());
        
        // Generate discount promotions (realistic patterns: more discounts near Halloween)
        generateDiscountPromotions(sales);
        log.info("Generated discount promotions");
        
        // Generate transactions and discount effectiveness
        generateTransactionsAndDiscounts(sales);
        log.info("Generated transactions and discount effectiveness");
        
        // Recalculate basket analysis for all historical data
        LocalDate startDate = sales.stream()
            .map(SaleRecord::getDate)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.of(2015, 10, 1));
        LocalDate endDate = sales.stream()
            .map(SaleRecord::getDate)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.of(2024, 10, 31));
        
        basketAnalysisService.recalculateBasketAnalysis(startDate, endDate);
        log.info("Recalculated basket analysis");
        
        log.info("Historical data generation complete!");
    }

    private List<SaleRecord> readHistoricalCsv() {
        List<SaleRecord> sales = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
        
        // Try to read from file system first (project root/data/)
        java.io.File csvFile = new java.io.File("data/oct_sales_2015_2024.csv");
        if (!csvFile.exists()) {
            // Try resources as fallback
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("data/oct_sales_2015_2024.csv")) {
                if (is == null) {
                    throw new RuntimeException("Could not find oct_sales_2015_2024.csv. " +
                        "Expected at: data/oct_sales_2015_2024.csv or in resources");
                }
                readFromInputStream(new InputStreamReader(is), sales, formatter);
            } catch (Exception e) {
                throw new RuntimeException("Error reading historical CSV from resources", e);
            }
        } else {
            // Read from file system
            try (BufferedReader reader = new BufferedReader(
                    new java.io.FileReader(csvFile))) {
                readFromInputStream(reader, sales, formatter);
            } catch (Exception e) {
                throw new RuntimeException("Error reading historical CSV from file system", e);
            }
        }
        
        return sales;
    }
    
    private void readFromInputStream(java.io.Reader reader, List<SaleRecord> sales, 
                                     DateTimeFormatter formatter) throws Exception {
        try (BufferedReader br = reader instanceof BufferedReader 
                ? (BufferedReader) reader 
                : new BufferedReader(reader)) {
            String line = br.readLine(); // Skip header
            
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                
                String productName = parts[0].trim();
                int units = Integer.parseInt(parts[1].trim());
                LocalDate date = LocalDate.parse(parts[2].trim(), formatter);
                
                SaleRecord sale = new SaleRecord(productName, units, date);
                sales.add(sale);
            }
        }
    }

    /**
     * Generate discount promotions with realistic patterns:
     * - More discounts in late October (Halloween week)
     * - Popular products get discounts more often
     * - Discounts typically last 3-7 days
     */
    private void generateDiscountPromotions(List<SaleRecord> sales) {
        Random random = new Random(42); // Seed for reproducibility
        Map<String, Integer> productPopularity = calculateProductPopularity(sales);
        
        List<Discount> discounts = new ArrayList<>();
        
        for (SaleRecord sale : sales) {
            LocalDate date = sale.getDate();
            String productName = sale.getProductName();
            int dayOfMonth = date.getDayOfMonth();
            
            // Higher probability of discount:
            // - Late October (days 25-31): 40% chance
            // - Mid October (days 15-24): 25% chance
            // - Early October (days 1-14): 15% chance
            // - Popular products: +10% chance
            double baseProbability = dayOfMonth >= 25 ? 0.40 : (dayOfMonth >= 15 ? 0.25 : 0.15);
            int popularity = productPopularity.getOrDefault(productName, 0);
            double popularityBoost = Math.min(0.10, popularity / 10000.0);
            double discountProbability = baseProbability + popularityBoost;
            
            if (random.nextDouble() < discountProbability) {
                // Choose discount percentage (higher discounts more likely near Halloween)
                BigDecimal discountPercent;
                if (dayOfMonth >= 28) {
                    // Halloween week: higher discounts
                    discountPercent = DISCOUNT_OPTIONS[random.nextInt(DISCOUNT_OPTIONS.length - 1) + 2]; // 20-30%
                } else if (dayOfMonth >= 20) {
                    discountPercent = DISCOUNT_OPTIONS[random.nextInt(DISCOUNT_OPTIONS.length - 1) + 1]; // 15-30%
                } else {
                    discountPercent = DISCOUNT_OPTIONS[random.nextInt(DISCOUNT_OPTIONS.length)]; // 10-30%
                }
                
                // Discount duration: 3-7 days
                int duration = 3 + random.nextInt(5);
                LocalDate endDate = date.plusDays(duration - 1);
                
                // Check if discount already exists for this product/date range
                boolean exists = discountRepository
                    .findByProductNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        productName, endDate, date)
                    .stream()
                    .anyMatch(d -> d.getDiscountPercent().compareTo(discountPercent) == 0);
                
                if (!exists) {
                    Discount discount = new Discount();
                    discount.setProductName(productName);
                    discount.setDiscountPercent(discountPercent);
                    discount.setStartDate(date);
                    discount.setEndDate(endDate);
                    discount.setDescription("Halloween promotion - " + discountPercent + "% off");
                    discounts.add(discount);
                }
            }
        }
        
        discountRepository.saveAll(discounts);
    }

    /**
     * Generate transactions by breaking down daily sales into individual transactions.
     * Also creates discount effectiveness records.
     */
    private void generateTransactionsAndDiscounts(List<SaleRecord> sales) {
        Random random = new Random(42);
        List<Transaction> transactions = new ArrayList<>();
        List<DiscountEffectiveness> effectivenessRecords = new ArrayList<>();
        
        // Group sales by date
        Map<LocalDate, List<SaleRecord>> salesByDate = sales.stream()
            .collect(Collectors.groupingBy(SaleRecord::getDate));
        
        for (Map.Entry<LocalDate, List<SaleRecord>> entry : salesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<SaleRecord> daySales = entry.getValue();
            
            // Get active discounts for this date
            List<Discount> activeDiscounts = discountRepository.findActiveDiscounts(date);
            Map<String, BigDecimal> discountMap = activeDiscounts.stream()
                .collect(Collectors.toMap(
                    Discount::getProductName,
                    Discount::getDiscountPercent,
                    (v1, v2) -> v1 // If multiple, take first
                ));
            
            // Generate transactions for this day
            // Each sale record represents daily units - break into multiple transactions
            for (SaleRecord sale : daySales) {
                String productName = sale.getProductName();
                int totalUnits = sale.getUnits();
                BigDecimal discountPercent = discountMap.getOrDefault(productName, BigDecimal.ZERO);
                
                // Break down into transactions (1-5 units per transaction typically)
                int remainingUnits = totalUnits;
                int transactionCount = Math.max(1, totalUnits / 3); // Rough estimate
                
                for (int i = 0; i < transactionCount && remainingUnits > 0; i++) {
                    Transaction transaction = new Transaction();
                    transaction.setTransactionDate(date);
                    // Random time between 8 AM and 9 PM
                    int hour = 8 + random.nextInt(14);
                    int minute = random.nextInt(60);
                    transaction.setTransactionTime(LocalTime.of(hour, minute));
                    
                    List<TransactionItem> items = new ArrayList<>();
                    
                    // Add the main product
                    int unitsForTransaction = Math.min(remainingUnits, 1 + random.nextInt(3));
                    remainingUnits -= unitsForTransaction;
                    
                    TransactionItem mainItem = new TransactionItem();
                    mainItem.setProductName(productName);
                    mainItem.setQuantity(unitsForTransaction);
                    mainItem.setUnitPrice(BigDecimal.valueOf(2.50 + random.nextDouble() * 3.0)); // $2.50-$5.50
                    mainItem.setDiscountPercent(discountPercent);
                    mainItem.setTransaction(transaction);
                    items.add(mainItem);
                    
                    // 60% chance to add associated items
                    if (random.nextDouble() < 0.6 && PRODUCT_ASSOCIATIONS.containsKey(productName)) {
                        List<String> associated = PRODUCT_ASSOCIATIONS.get(productName);
                        // 40% chance for each associated item
                        for (String associatedProduct : associated) {
                            if (random.nextDouble() < 0.4) {
                                TransactionItem assocItem = new TransactionItem();
                                assocItem.setProductName(associatedProduct);
                                assocItem.setQuantity(1 + random.nextInt(2));
                                assocItem.setUnitPrice(BigDecimal.valueOf(1.50 + random.nextDouble() * 4.0));
                                assocItem.setDiscountPercent(BigDecimal.ZERO); // Associated items usually not discounted
                                assocItem.setTransaction(transaction);
                                items.add(assocItem);
                            }
                        }
                    }
                    
                    // 20% chance to add random grocery item
                    if (random.nextDouble() < 0.2) {
                        String groceryItem = GROCERY_ITEMS.get(random.nextInt(GROCERY_ITEMS.size()));
                        TransactionItem grocery = new TransactionItem();
                        grocery.setProductName(groceryItem);
                        grocery.setQuantity(1);
                        grocery.setUnitPrice(BigDecimal.valueOf(2.00 + random.nextDouble() * 5.0));
                        grocery.setDiscountPercent(BigDecimal.ZERO);
                        grocery.setTransaction(transaction);
                        items.add(grocery);
                    }
                    
                    transaction.setItems(items);
                    transactions.add(transaction);
                }
                
                // Create discount effectiveness record
                if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {
                    // Calculate baseline (average units without discount for this product/date in other years)
                    int baselineUnits = calculateBaselineUnits(productName, date);
                    if (baselineUnits == 0) baselineUnits = totalUnits; // Fallback
                    
                    BigDecimal salesLift = BigDecimal.valueOf(totalUnits - baselineUnits)
                        .divide(BigDecimal.valueOf(baselineUnits), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    
                    BigDecimal avgUnitPrice = BigDecimal.valueOf(3.50); // Average price
                    BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                        discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    );
                    BigDecimal revenue = avgUnitPrice.multiply(discountMultiplier)
                        .multiply(BigDecimal.valueOf(totalUnits));
                    
                    DiscountEffectiveness effectiveness = new DiscountEffectiveness();
                    effectiveness.setProductName(productName);
                    effectiveness.setDiscountPercent(discountPercent);
                    effectiveness.setDate(date);
                    effectiveness.setUnitsSold(totalUnits);
                    effectiveness.setRevenue(revenue);
                    effectiveness.setAvgUnitPrice(avgUnitPrice);
                    effectiveness.setSalesLiftPercent(salesLift);
                    effectivenessRecords.add(effectiveness);
                }
            }
        }
        
        transactionRepository.saveAll(transactions);
        discountEffectivenessRepository.saveAll(effectivenessRecords);
    }

    private Map<String, Integer> calculateProductPopularity(List<SaleRecord> sales) {
        return sales.stream()
            .collect(Collectors.groupingBy(
                SaleRecord::getProductName,
                Collectors.summingInt(SaleRecord::getUnits)
            ));
    }

    private int calculateBaselineUnits(String productName, LocalDate date) {
        // Get average units for this product on the same day of month across other years
        int dayOfMonth = date.getDayOfMonth();
        int year = date.getYear();
        
        List<SaleRecord> similarDates = saleRepository.findAll().stream()
            .filter(s -> s.getProductName().equals(productName))
            .filter(s -> s.getDate().getDayOfMonth() == dayOfMonth)
            .filter(s -> s.getDate().getYear() != year)
            .collect(Collectors.toList());
        
        if (similarDates.isEmpty()) {
            return 0;
        }
        
        return (int) similarDates.stream()
            .mapToInt(SaleRecord::getUnits)
            .average()
            .orElse(0);
    }
}

