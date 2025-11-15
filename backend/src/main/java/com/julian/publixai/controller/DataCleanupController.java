package com.julian.publixai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.julian.publixai.repository.BasketAnalysisRepository;
import com.julian.publixai.repository.DiscountEffectivenessRepository;
import com.julian.publixai.repository.DiscountRepository;
import com.julian.publixai.repository.TransactionItemRepository;
import com.julian.publixai.repository.TransactionRepository;

/**
 * DataCleanupController
 * 
 * Endpoint to delete all discount and frequently bought together data.
 * Used for resetting the database before generating fresh synthetic data.
 * 
 * WARNING: This permanently deletes all discount and basket analysis data.
 */
@RestController
@RequestMapping("/api/admin/cleanup")
public class DataCleanupController {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupController.class);

    private final DiscountRepository discountRepository;
    private final DiscountEffectivenessRepository discountEffectivenessRepository;
    private final BasketAnalysisRepository basketAnalysisRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionItemRepository transactionItemRepository;

    public DataCleanupController(
            DiscountRepository discountRepository,
            DiscountEffectivenessRepository discountEffectivenessRepository,
            BasketAnalysisRepository basketAnalysisRepository,
            TransactionRepository transactionRepository,
            TransactionItemRepository transactionItemRepository) {
        this.discountRepository = discountRepository;
        this.discountEffectivenessRepository = discountEffectivenessRepository;
        this.basketAnalysisRepository = basketAnalysisRepository;
        this.transactionRepository = transactionRepository;
        this.transactionItemRepository = transactionItemRepository;
    }

    /**
     * DELETE /api/admin/cleanup/discounts-and-basket
     * Delete all discount and frequently bought together data.
     * 
     * This will delete:
     * - All Discount records
     * - All DiscountEffectiveness records
     * - All BasketAnalysis records
     * - All Transaction records
     * - All TransactionItem records
     * 
     * Use this before generating fresh synthetic data.
     */
    @DeleteMapping("/discounts-and-basket")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteDiscountsAndBasketData() {
        try {
            log.info("Starting database cleanup: deleting all synthetic data...");
            
            // Get counts before deletion
            long transactionItemCount = transactionItemRepository.count();
            long transactionCount = transactionRepository.count();
            long basketAnalysisCount = basketAnalysisRepository.count();
            long discountEffectivenessCount = discountEffectivenessRepository.count();
            long discountCount = discountRepository.count();
            
            log.info("Current record counts:");
            log.info("  - TransactionItems: {}", transactionItemCount);
            log.info("  - Transactions: {}", transactionCount);
            log.info("  - BasketAnalyses: {}", basketAnalysisCount);
            log.info("  - DiscountEffectiveness: {}", discountEffectivenessCount);
            log.info("  - Discounts: {}", discountCount);
            
            // Delete in order to respect foreign key constraints
            // Transaction items must be deleted before transactions
            log.info("Deleting TransactionItems...");
            transactionItemRepository.deleteAll();
            
            log.info("Deleting Transactions...");
            transactionRepository.deleteAll();
            
            log.info("Deleting BasketAnalyses...");
            basketAnalysisRepository.deleteAll();
            
            log.info("Deleting DiscountEffectiveness...");
            discountEffectivenessRepository.deleteAll();
            
            log.info("Deleting Discounts...");
            discountRepository.deleteAll();
            
            log.info("Database cleanup completed successfully!");
            
        } catch (Exception e) {
            log.error("Error during database cleanup", e);
            throw new RuntimeException("Failed to clean database: " + e.getMessage(), e);
        }
    }
}
