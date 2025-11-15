package com.julian.publixai.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.julian.publixai.dto.BasketAnalysisResponse;
import com.julian.publixai.model.BasketAnalysis;
import com.julian.publixai.repository.BasketAnalysisRepository;
import com.julian.publixai.service.BasketAnalysisService;
import com.julian.publixai.service.SampleDataService;
import com.julian.publixai.service.SimpleSyntheticDataService;

import jakarta.validation.constraints.NotBlank;

/**
 * BasketAnalysis API
 * 
 * Endpoints for frequently bought together analysis and sample data generation.
 */
@RestController
@RequestMapping("/api/basket")
@Validated
public class BasketAnalysisController {

        private static final Logger log = LoggerFactory.getLogger(BasketAnalysisController.class);

        private final BasketAnalysisService basketAnalysisService;
        private final SampleDataService sampleDataService;
        private final SimpleSyntheticDataService simpleSyntheticDataService;
        private final BasketAnalysisRepository basketAnalysisRepository;

        public BasketAnalysisController(BasketAnalysisService basketAnalysisService,
                        SampleDataService sampleDataService,
                        SimpleSyntheticDataService simpleSyntheticDataService,
                        BasketAnalysisRepository basketAnalysisRepository) {
                this.basketAnalysisService = basketAnalysisService;
                this.sampleDataService = sampleDataService;
                this.simpleSyntheticDataService = simpleSyntheticDataService;
                this.basketAnalysisRepository = basketAnalysisRepository;
        }

        /**
         * GET /api/basket/frequently-bought-together?productName={name}
         * Get frequently bought together items for a specific product.
         * Automatically generates sample data if none exists globally.
         * 
         * Transaction is kept open here to ensure entities remain attached during
         * Jackson serialization.
         */
        @GetMapping("/frequently-bought-together")
        @Transactional(readOnly = true)
        public BasketAnalysisResponse getFrequentlyBoughtTogether(
                        @RequestParam("productName") @NotBlank String productName,
                        @RequestParam(value = "minConfidence", required = false) Double minConfidence) {

                log.info("Received request for frequently bought together items - Product: '{}', minConfidence: {}",
                                productName, minConfidence);

                // Check if synthetic data exists
                // Only generate ONCE if data doesn't exist - never regenerate automatically
                long totalTransactions = sampleDataService.countTransactions();
                long totalBasketAnalyses = basketAnalysisService.countAll();

                log.info("Basket data check - Transactions: {}, Basket Analyses: {}, Needs generation: {}",
                                totalTransactions, totalBasketAnalyses,
                                (totalTransactions == 0 && totalBasketAnalyses == 0));

                // Only generate if we have NO data at all (first time setup)
                // Once data exists, never auto-regenerate (prevents infinite loops)
                boolean needsGeneration = totalTransactions == 0 && totalBasketAnalyses == 0;

                if (needsGeneration) {
                        // Generate simple synthetic data: exactly 3,000 transactions + synthetic
                        // associations
                        // This only runs ONCE on first access
                        simpleSyntheticDataService.generateSimpleSyntheticData();
                }

                // Fetch the data for this product
                // Apply minConfidence filter if provided
                BasketAnalysisResponse response;
                if (minConfidence != null && minConfidence > 0) {
                        response = basketAnalysisService.getFrequentlyBoughtTogether(productName, minConfidence);
                } else {
                        response = basketAnalysisService.getFrequentlyBoughtTogether(productName);
                }

                log.info("Service returned response. Response is null: {}, Items is null: {}, Items size: {}",
                                response == null,
                                response != null && response.getItems() == null,
                                response != null && response.getItems() != null ? response.getItems().size() : -1);

                // Verify response integrity
                if (response == null) {
                        log.error("Service returned null response");
                        return new BasketAnalysisResponse(new java.util.ArrayList<>(), false);
                }

                if (response.getItems() == null) {
                        log.error("Response items is null");
                        response.setItems(new java.util.ArrayList<>());
                }

                // Log response details
                int itemsCount = response.getItems() != null ? response.getItems().size() : 0;
                String firstItem = (response.getItems() != null && !response.getItems().isEmpty())
                                ? response.getItems().get(0).getAssociatedProduct()
                                : "N/A";

                // Log DTO conversion status
                if (itemsCount > 0) {
                        log.debug("Response contains {} DTOs (not JPA entities). First item: '{}'", itemsCount,
                                        firstItem);
                }
                log.info("Controller returning response - items count: {}, isPredicted: {}, first item: '{}'",
                                itemsCount, response.isPredicted(), firstItem);

                // Log warning if historical product returns empty results (may indicate data or
                // serialization issue)
                if (itemsCount == 0 && !response.isPredicted()) {
                        log.warn("Response items is empty for historical product '{}'. This may indicate a serialization issue.",
                                        productName);
                }

                return response;
        }

        /**
         * GET /api/basket/debug/product-names
         * Administrative endpoint to list all available primary products in basket
         * analysis.
         * Used for troubleshooting product name matching issues.
         * 
         * Optional query parameter: ?productName={name} - Shows basket analysis records
         * for specific product
         */
        @GetMapping("/debug/product-names")
        public Map<String, Object> getAvailableProductNames(
                        @RequestParam(value = "productName", required = false) String productName) {

                Map<String, Object> response = new HashMap<>();

                // Get all basket analyses and extract distinct primary products
                List<String> primaryProducts = new java.util.ArrayList<>();
                if (basketAnalysisService.countAll() > 0) {
                        primaryProducts = basketAnalysisRepository.findAll()
                                        .stream()
                                        .map(BasketAnalysis::getPrimaryProduct)
                                        .distinct()
                                        .sorted()
                                        .collect(Collectors.toList());
                }

                response.put("availablePrimaryProducts", primaryProducts);
                response.put("totalBasketAnalyses", basketAnalysisService.countAll());
                response.put("totalTransactions", sampleDataService.countTransactions());

                // If productName is provided, show detailed basket analysis records for that
                // product
                if (productName != null && !productName.trim().isEmpty()) {
                        log.debug("Searching for product '{}'", productName);

                        // Try exact match
                        List<BasketAnalysis> exactMatch = basketAnalysisRepository
                                        .findByPrimaryProductOrderByConfidenceScoreDesc(productName);

                        // Try case-insensitive match
                        List<BasketAnalysis> caseInsensitiveMatch = basketAnalysisRepository
                                        .findByPrimaryProductIgnoreCaseOrderByCoOccurrenceCountDesc(productName);

                        // Try fuzzy match
                        List<BasketAnalysis> fuzzyMatch = basketAnalysisRepository
                                        .findByPrimaryProductContainingIgnoreCaseOrderByCoOccurrenceCountDesc(
                                                        productName);

                        // Try reverse lookup
                        List<BasketAnalysis> reverseMatch = basketAnalysisRepository
                                        .findByAssociatedProductContainingIgnoreCaseOrderByCoOccurrenceCountDesc(
                                                        productName);

                        // Build detailed response
                        Map<String, Object> productDetails = new HashMap<>();
                        productDetails.put("searchedProductName", productName);
                        productDetails.put("exactMatchCount", exactMatch.size());
                        productDetails.put("exactMatchRecords", exactMatch.stream()
                                        .limit(10)
                                        .map(ba -> Map.of(
                                                        "primaryProduct", ba.getPrimaryProduct(),
                                                        "associatedProduct", ba.getAssociatedProduct(),
                                                        "coOccurrenceCount", ba.getCoOccurrenceCount(),
                                                        "confidenceScore", ba.getConfidenceScore()))
                                        .collect(Collectors.toList()));
                        productDetails.put("caseInsensitiveMatchCount", caseInsensitiveMatch.size());
                        productDetails.put("caseInsensitiveMatchRecords", caseInsensitiveMatch.stream()
                                        .limit(10)
                                        .map(ba -> Map.of(
                                                        "primaryProduct", ba.getPrimaryProduct(),
                                                        "associatedProduct", ba.getAssociatedProduct(),
                                                        "coOccurrenceCount", ba.getCoOccurrenceCount()))
                                        .collect(Collectors.toList()));
                        productDetails.put("fuzzyMatchCount", fuzzyMatch.size());
                        productDetails.put("fuzzyMatchRecords", fuzzyMatch.stream()
                                        .limit(10)
                                        .map(ba -> Map.of(
                                                        "primaryProduct", ba.getPrimaryProduct(),
                                                        "associatedProduct", ba.getAssociatedProduct(),
                                                        "coOccurrenceCount", ba.getCoOccurrenceCount()))
                                        .collect(Collectors.toList()));
                        productDetails.put("reverseMatchCount", reverseMatch.size());
                        productDetails.put("reverseMatchRecords", reverseMatch.stream()
                                        .limit(10)
                                        .map(ba -> Map.of(
                                                        "primaryProduct", ba.getPrimaryProduct(),
                                                        "associatedProduct", ba.getAssociatedProduct(),
                                                        "coOccurrenceCount", ba.getCoOccurrenceCount()))
                                        .collect(Collectors.toList()));

                        // Get distinct product name variations found
                        List<String> productNameVariations = new java.util.ArrayList<>();
                        productNameVariations.addAll(exactMatch.stream().map(BasketAnalysis::getPrimaryProduct)
                                        .distinct().collect(Collectors.toList()));
                        productNameVariations
                                        .addAll(caseInsensitiveMatch.stream().map(BasketAnalysis::getPrimaryProduct)
                                                        .distinct().collect(Collectors.toList()));
                        productNameVariations.addAll(fuzzyMatch.stream().map(BasketAnalysis::getPrimaryProduct)
                                        .distinct().collect(Collectors.toList()));
                        productDetails.put("productNameVariations", productNameVariations.stream().distinct().sorted()
                                        .collect(Collectors.toList()));

                        response.put("productDetails", productDetails);
                }

                return response;
        }

        /**
         * GET /api/basket/debug/test-serialization?productName={name}
         * Administrative endpoint to verify JSON serialization independently.
         * Returns serialization details for troubleshooting purposes.
         */
        @GetMapping("/debug/test-serialization")
        public Map<String, Object> testSerialization(
                        @RequestParam("productName") @NotBlank String productName) {

                log.info("Serialization verification endpoint called for product: '{}'", productName);

                BasketAnalysisResponse response = basketAnalysisService.getFrequentlyBoughtTogether(productName);

                Map<String, Object> result = new HashMap<>();
                result.put("productName", productName);
                result.put("itemsCount", response.getItems() != null ? response.getItems().size() : 0);
                result.put("isPredicted", response.isPredicted());
                result.put("items", response.getItems());
                result.put("firstItem", (response.getItems() != null && !response.getItems().isEmpty())
                                ? Map.of(
                                                "associatedProduct", response.getItems().get(0).getAssociatedProduct(),
                                                "coOccurrenceCount", response.getItems().get(0).getCoOccurrenceCount())
                                : null);

                log.info("Serialization verification response - items count: {}, isPredicted: {}",
                                result.get("itemsCount"), result.get("isPredicted"));

                return result;
        }

        /**
         * POST /api/basket/recalculate
         * Recalculate basket analysis from transaction data.
         */
        @PostMapping("/recalculate")
        @ResponseStatus(HttpStatus.OK)
        public void recalculateBasketAnalysis(
                        @RequestParam("startDate") @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
                        @RequestParam("endDate") @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
                basketAnalysisService.recalculateBasketAnalysis(startDate, endDate);
        }

        /**
         * POST /api/basket/generate-simple-synthetic-data
         * Generate simple synthetic data: exactly 3,000 transactions and synthetic
         * frequently bought together items.
         * 
         * This is a one-time setup that creates reliable, simple synthetic data.
         * No complex logic, no date ranges - just simple, reliable data generation.
         * 
         * @param forceRegenerate If true, deletes existing data and regenerates. If
         *                        false (default), only generates if no data exists.
         */
        @PostMapping("/generate-simple-synthetic-data")
        @ResponseStatus(HttpStatus.CREATED)
        public void generateSimpleSyntheticData(
                        @RequestParam(value = "forceRegenerate", defaultValue = "false") boolean forceRegenerate) {
                if (forceRegenerate) {
                        log.info("Force regeneration requested. Regenerating complete synthetic dataset...");
                        simpleSyntheticDataService.generateCompleteHistoricalDataset(true);
                } else {
                        log.info("Generating synthetic dataset if missing...");
                        simpleSyntheticDataService.generateCompleteHistoricalDataset(false);
                }
        }

        /**
         * POST /api/basket/generate-sample-data
         * Generate sample transaction data for specific date ranges.
         * 
         * This endpoint allows generating transactions for specific date ranges,
         * useful for generating predictions for new products or populating test data.
         * 
         * @param startDate          Start date (optional, defaults to current month
         *                           start)
         * @param endDate            End date (optional, defaults to current month end)
         * @param transactionsPerDay Number of transactions per day (default: 50)
         */
        @PostMapping("/generate-sample-data")
        @ResponseStatus(HttpStatus.CREATED)
        public void generateSampleData(
                        @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
                        @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
                        @RequestParam(value = "transactionsPerDay", defaultValue = "50") int transactionsPerDay) {
                if (startDate == null || endDate == null) {
                        // Default to current month
                        LocalDate now = LocalDate.now();
                        LocalDate monthStart = now.withDayOfMonth(1);
                        LocalDate monthEnd = now.withDayOfMonth(now.lengthOfMonth());
                        sampleDataService.generateSampleTransactions(monthStart, monthEnd, transactionsPerDay);
                } else {
                        sampleDataService.generateSampleTransactions(startDate, endDate, transactionsPerDay);
                }
        }
}
