package com.julian.publixai.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.julian.publixai.repository.BasketAnalysisRepository;
import com.julian.publixai.repository.DiscountEffectivenessRepository;
import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.repository.SaleRepository;
import com.julian.publixai.repository.TransactionRepository;
import com.julian.publixai.service.SimpleSyntheticDataService;

/**
 * DataInitializer
 * 
 * Purpose: ONE-TIME initialization of synthetic data on first application
 * startup.
 * 
 * This initializer runs automatically when the Spring Boot application starts.
 * It checks if historical CSV data (3,100 rows) has been loaded but synthetic
 * supplementary data does not exist. If so, it generates:
 * 
 * 1. Transaction history (~3,000 records) for frequently bought together
 * analysis
 * 2. Basket analysis data (~210 records) derived from transactions
 * 3. Lightweight discount data (~1,500 records) for discount insights
 * 4. Discount records (~300 records) for year-by-year display
 * 
 * The generation runs ONCE and never again. All data is persisted to the
 * database
 * and reused for subsequent application restarts.
 * 
 * For new products added later, predictions are generated on-demand (in-memory
 * only).
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SaleRepository saleRepository;
    private final TransactionRepository transactionRepository;
    private final BasketAnalysisRepository basketAnalysisRepository;
    private final DiscountRepository discountRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final SimpleSyntheticDataService simpleSyntheticDataService;

    public DataInitializer(
            SaleRepository saleRepository,
            TransactionRepository transactionRepository,
            BasketAnalysisRepository basketAnalysisRepository,
            DiscountRepository discountRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            SimpleSyntheticDataService simpleSyntheticDataService) {
        this.saleRepository = saleRepository;
        this.transactionRepository = transactionRepository;
        this.basketAnalysisRepository = basketAnalysisRepository;
        this.discountRepository = discountRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.simpleSyntheticDataService = simpleSyntheticDataService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========================================");
        log.info("DataInitializer: Checking if one-time synthetic data generation is needed...");
        log.info("========================================");

        // Check current database state
        long salesCount = saleRepository.count();
        long transactionCount = transactionRepository.count();
        long basketAnalysisCount = basketAnalysisRepository.count();
        long discountCount = discountRepository.count();
        long discountEffectivenessCount = discountEffectivenessRepository.count();

        log.info("Current database state:");
        log.info("  - Sales (CSV): {}", salesCount);
        log.info("  - Transactions: {}", transactionCount);
        log.info("  - Basket Analyses: {}", basketAnalysisCount);
        log.info("  - Discounts: {}", discountCount);
        log.info("  - Discount Effectiveness: {}", discountEffectivenessCount);

        // Criteria for one-time generation:
        // 1. CSV data is loaded (salesCount >= 3000)
        // 2. Check what synthetic data is missing
        boolean hasHistoricalCsvData = salesCount >= 3000;
        boolean needsTransactions = transactionCount == 0 && basketAnalysisCount == 0;
        // Check if discounts are missing OR if year coverage is incomplete (2015-2024)
        boolean needsDiscounts = (discountCount == 0 && discountEffectivenessCount == 0) ||
                (discountEffectivenessCount > 0 && !checkDiscountYearCoverage());
        boolean needsSyntheticData = needsTransactions && needsDiscounts;

        if (hasHistoricalCsvData && needsSyntheticData) {
            log.info("========================================");
            log.info("FIRST STARTUP DETECTED");
            log.info("CSV historical data found ({} sales records)", salesCount);
            log.info("No synthetic data found - generating ONE-TIME dataset...");
            log.info("========================================");

            try {
                // Generate complete synthetic dataset (transactions, basket analyses,
                // discounts)
                // This runs ONCE and saves ~5,000 total records to the database
                simpleSyntheticDataService.generateCompleteHistoricalDataset();

                // Log final counts
                long finalTransactions = transactionRepository.count();
                long finalBasketAnalyses = basketAnalysisRepository.count();
                long finalDiscounts = discountRepository.count();
                long finalDiscountEffectiveness = discountEffectivenessRepository.count();
                long totalSynthetic = finalTransactions + finalBasketAnalyses + finalDiscounts
                        + finalDiscountEffectiveness;

                log.info("========================================");
                log.info("ONE-TIME SYNTHETIC DATA GENERATION COMPLETE");
                log.info("========================================");
                log.info("Generated synthetic data:");
                log.info("  - Transactions: {}", finalTransactions);
                log.info("  - Basket Analyses: {}", finalBasketAnalyses);
                log.info("  - Discounts: {}", finalDiscounts);
                log.info("  - Discount Effectiveness: {}", finalDiscountEffectiveness);
                log.info("Total Synthetic Records: {}", totalSynthetic);
                log.info("Total Database Records: {} (CSV) + {} (synthetic) = {}",
                        salesCount, totalSynthetic, salesCount + totalSynthetic);
                log.info("========================================");
                log.info("This data is now saved and will NOT regenerate on restart.");
                log.info("For new products, predictions will be generated on-demand (in-memory only).");
                log.info("========================================");

            } catch (Exception e) {
                log.error("========================================");
                log.error("Failed to generate one-time synthetic data", e);
                log.error("========================================");
                log.error("Application will continue but discount/basket features may not work correctly.");
                log.error(
                        "You can manually trigger data generation via POST /api/basket/generate-simple-synthetic-data?forceRegenerate=true");
                log.error("========================================");
            }

        } else if (hasHistoricalCsvData && needsDiscounts) {
            // Transactions exist but discounts are missing OR year coverage is incomplete -
            // generate/regenerate discount data
            log.info("========================================");
            log.info("MISSING OR INCOMPLETE DISCOUNT DATA DETECTED");
            if (discountEffectivenessCount == 0) {
                log.info("Transactions and basket analyses exist, but discount data is missing.");
            } else {
                log.info("Discount data exists but year coverage is incomplete (missing years 2015-2019).");
            }
            log.info("Generating/regenerating discount data to ensure complete year coverage...");
            log.info("========================================");

            try {
                // Force regenerate if year coverage is incomplete
                boolean forceRegenerate = discountEffectivenessCount > 0 && !checkDiscountYearCoverage();
                simpleSyntheticDataService.generateLightweightDiscountData(forceRegenerate);

                long finalDiscounts = discountRepository.count();
                long finalDiscountEffectiveness = discountEffectivenessRepository.count();

                log.info("========================================");
                log.info("DISCOUNT DATA GENERATION COMPLETE");
                log.info("========================================");
                log.info("Generated discount data:");
                log.info("  - Discounts: {}", finalDiscounts);
                log.info("  - Discount Effectiveness: {}", finalDiscountEffectiveness);
                log.info("========================================");

            } catch (Exception e) {
                log.error("========================================");
                log.error("Failed to generate discount data", e);
                log.error("========================================");
                log.error("Application will continue but discount features may not work correctly.");
                log.error("========================================");
            }

        } else if (!hasHistoricalCsvData) {
            log.warn("========================================");
            log.warn("Historical CSV data not found");
            log.warn("Expected >= 3000 sales records, found: {}", salesCount);
            log.warn("Synthetic data generation skipped.");
            log.warn("Please ensure Flyway migrations have run to load data/oct_sales_2015_2024.csv");
            log.warn("========================================");
        } else {
            log.info("========================================");
            log.info("Synthetic data already exists. Skipping one-time generation.");
            log.info("========================================");
            log.info("Database records:");
            log.info("  - Sales (CSV): {}", salesCount);
            log.info("  - Transactions: {}", transactionCount);
            log.info("  - Basket Analyses: {}", basketAnalysisCount);
            log.info("  - Discounts: {}", discountCount);
            log.info("  - Discount Effectiveness: {}", discountEffectivenessCount);
            log.info("Total: {}",
                    salesCount + transactionCount + basketAnalysisCount + discountCount + discountEffectivenessCount);
            log.info("========================================");
        }

        log.info("DataInitializer: Initialization complete.");
        log.info("Application ready.");
    }

    /**
     * Check if discount effectiveness data covers all required years (2015-2024).
     * Returns true if all years are covered, false if any years are missing.
     */
    private boolean checkDiscountYearCoverage() {
        if (discountEffectivenessRepository.count() == 0) {
            return false; // No data at all
        }

        // Query distinct years from discount_effectiveness table
        List<Integer> existingYears = discountEffectivenessRepository.findAll()
                .stream()
                .map(de -> de.getDate().getYear())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Check if all years 2015-2024 are present
        Set<Integer> requiredYears = IntStream.rangeClosed(2015, 2024)
                .boxed()
                .collect(Collectors.toSet());
        Set<Integer> existingYearsSet = new HashSet<>(existingYears);

        Set<Integer> missingYears = new HashSet<>(requiredYears);
        missingYears.removeAll(existingYearsSet);

        if (!missingYears.isEmpty()) {
            log.warn("Missing discount data for years: {}. Existing years: {}", missingYears, existingYears);
            return false; // Coverage incomplete
        }

        log.debug("Discount year coverage complete. All years 2015-2024 are present.");
        return true; // All years covered
    }
}
