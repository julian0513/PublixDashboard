package com.julian.publixai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.julian.publixai.dto.BasketAnalysisDTO;
import com.julian.publixai.dto.BasketAnalysisResponse;
import com.julian.publixai.model.BasketAnalysis;
import com.julian.publixai.model.Transaction;
import com.julian.publixai.model.TransactionItem;
import com.julian.publixai.repository.BasketAnalysisRepository;
import com.julian.publixai.repository.TransactionRepository;

/**
 * BasketAnalysisService
 * 
 * Purpose: Calculate and manage frequently bought together relationships.
 * Uses association rule mining (confidence and support scores).
 */
@Service
public class BasketAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(BasketAnalysisService.class);

    private final BasketAnalysisRepository basketAnalysisRepository;
    private final TransactionRepository transactionRepository;
    private final NewProductPredictionService newProductPredictionService;

    public BasketAnalysisService(
            BasketAnalysisRepository basketAnalysisRepository,
            TransactionRepository transactionRepository,
            NewProductPredictionService newProductPredictionService) {
        this.basketAnalysisRepository = basketAnalysisRepository;
        this.transactionRepository = transactionRepository;
        this.newProductPredictionService = newProductPredictionService;
    }

    /**
     * Get frequently bought together items for a product.
     * Returns top 10 items sorted by co-occurrence count (descending).
     * Uses a lower threshold (10+) to ensure we can always show top 10.
     * 
     * For new products, generates predicted associations.
     * 
     * NOTE: Transaction is managed at the controller level to keep session open during serialization.
     */
    public BasketAnalysisResponse getFrequentlyBoughtTogether(String productName) {
        log.debug("Searching for frequently bought together items for product: '{}'", productName);
        long totalCount = basketAnalysisRepository.count();
        log.debug("Total basket analyses in database: {}", totalCount);
        
        // Normalize product name for consistent matching
        String normalizedProductName = normalizeProductName(productName);
        log.debug("Normalized product name: '{}' -> '{}'", productName, normalizedProductName);
        
        // Dynamic threshold: if we have many records, use 5; if few, use 2
        final int MIN_CO_OCCURRENCE = totalCount > 100 ? 5 : 2;
        final int TOP_K = 10; // Always return top 10
        
        log.info("Searching for frequently bought together items - Product: '{}', Normalized: '{}', Threshold: {}, Total basket analyses in DB: {}", 
                productName, normalizedProductName, MIN_CO_OCCURRENCE, totalCount);
        
        // Initialize variables for tracking (for final logging)
        List<BasketAnalysis> caseInsensitiveMatchUnfiltered = new ArrayList<>();
        List<BasketAnalysis> partialMatchUnfiltered = new ArrayList<>();
        List<BasketAnalysis> reverseMatchUnfiltered = new ArrayList<>();
        
        // Try exact match first (with normalized product name for consistent matching)
        List<BasketAnalysis> exactMatch = basketAnalysisRepository
                .findByPrimaryProductOrderByConfidenceScoreDesc(normalizedProductName);
        log.info("Exact match (normalized) found {} items", exactMatch.size());
        if (!exactMatch.isEmpty()) {
            log.debug("Sample exact match product names: {}", 
                    exactMatch.stream().limit(3).map(BasketAnalysis::getPrimaryProduct).collect(Collectors.toList()));
            log.debug("Sample co-occurrence counts: {}", 
                    exactMatch.stream().limit(3).map(BasketAnalysis::getCoOccurrenceCount).collect(Collectors.toList()));
        }
        
        if (!exactMatch.isEmpty()) {
            // Filter by threshold and sort by count (descending), limit to top 10
            List<BasketAnalysis> filtered = exactMatch.stream()
                    .filter(ba -> ba.getCoOccurrenceCount() >= MIN_CO_OCCURRENCE)
                    .sorted((a, b) -> b.getCoOccurrenceCount() - a.getCoOccurrenceCount())
                    .limit(TOP_K)
                    .collect(Collectors.toList());
            
            // Fallback: if filtered is empty but we have unfiltered results, return top 10 unfiltered
            if (filtered.isEmpty() && !exactMatch.isEmpty()) {
                log.warn("All {} exact matches filtered out by threshold {}. Returning top {} unfiltered results.", 
                        exactMatch.size(), MIN_CO_OCCURRENCE, TOP_K);
                List<BasketAnalysis> unfiltered = exactMatch.stream()
                        .sorted((a, b) -> b.getCoOccurrenceCount() - a.getCoOccurrenceCount())
                        .limit(TOP_K)
                        .collect(Collectors.toList());
                
                // Create new ArrayList copy to ensure entities are materialized
                List<BasketAnalysis> materialized = new ArrayList<>(unfiltered);
                // Force materialization of all entities
                for (BasketAnalysis ba : materialized) {
                    ba.getAssociatedProduct();
                    ba.getCoOccurrenceCount();
                    ba.getConfidenceScore();
                    ba.getSupportScore();
                }
                
                log.info("Created materialized list with {} items (original unfiltered had {})", 
                        materialized.size(), unfiltered.size());
                
                // Verify first entity is fully materialized (log actual data)
                if (!materialized.isEmpty()) {
                    BasketAnalysis first = materialized.get(0);
                    log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                            first.getPrimaryProduct(), first.getAssociatedProduct(), 
                            first.getCoOccurrenceCount(), first.getConfidenceScore());
                    // Verify entity is not a Hibernate proxy
                    if (first.getClass().getName().contains("HibernateProxy")) {
                        log.warn("Entity is still a Hibernate proxy after materialization");
                    }
                }
                
                log.info("Returning {} unfiltered items (co-occurrence counts: {})", 
                        materialized.size(),
                        materialized.stream().map(BasketAnalysis::getCoOccurrenceCount).collect(Collectors.toList()));
                
                // Convert entities to DTOs for serialization
                List<BasketAnalysisDTO> dtos = materialized.stream()
                        .map(BasketAnalysisDTO::from)
                        .collect(Collectors.toList());
                
                log.info("Converted {} entities to DTOs. DTO list size: {}", materialized.size(), dtos.size());
                if (!dtos.isEmpty()) {
                    BasketAnalysisDTO firstDto = dtos.get(0);
                    log.info("First DTO - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                            firstDto.getPrimaryProduct(), firstDto.getAssociatedProduct(), 
                            firstDto.getCoOccurrenceCount(), firstDto.getConfidenceScore());
                } else {
                    log.error("DTO list is empty after conversion. Materialized had {} items", materialized.size());
                }
                
                BasketAnalysisResponse response = new BasketAnalysisResponse(dtos, false);
                log.info("Created BasketAnalysisResponse with {} DTOs", response.getItems() != null ? response.getItems().size() : 0);
                return response;
            }
            
            if (!filtered.isEmpty()) {
                // Create new ArrayList copy to ensure entities are materialized
                List<BasketAnalysis> materialized = new ArrayList<>(filtered);
                // Force materialization of all entities
                for (BasketAnalysis ba : materialized) {
                    ba.getAssociatedProduct();
                    ba.getCoOccurrenceCount();
                    ba.getConfidenceScore();
                    ba.getSupportScore();
                }
                
                log.info("Created materialized list with {} items (original filtered had {})", 
                        materialized.size(), filtered.size());
                
                // Verify list is not empty
                if (materialized.isEmpty() && !filtered.isEmpty()) {
                    log.error("Materialized list is empty but filtered had {} items", filtered.size());
                }
                
                // Verify first entity is fully materialized (log actual data)
                if (!materialized.isEmpty()) {
                    BasketAnalysis first = materialized.get(0);
                    log.info("About to return {} filtered items from exact match. First item: {}", 
                            materialized.size(), first.getAssociatedProduct());
                    log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                            first.getPrimaryProduct(), first.getAssociatedProduct(), 
                            first.getCoOccurrenceCount(), first.getConfidenceScore());
                    // Verify entity is not a Hibernate proxy
                    if (first.getClass().getName().contains("HibernateProxy")) {
                        log.warn("Entity is still a Hibernate proxy after materialization");
                    }
                }
                
                log.info("Returning {} filtered items from exact match (co-occurrence counts: {})", 
                        materialized.size(),
                        materialized.stream().map(BasketAnalysis::getCoOccurrenceCount).collect(Collectors.toList()));
                
                // Convert entities to DTOs for serialization
                List<BasketAnalysisDTO> dtos = materialized.stream()
                        .map(BasketAnalysisDTO::from)
                        .collect(Collectors.toList());
                
                log.info("Converted {} entities to DTOs. DTO list size: {}", materialized.size(), dtos.size());
                if (!dtos.isEmpty()) {
                    BasketAnalysisDTO firstDto = dtos.get(0);
                    log.info("First DTO - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                            firstDto.getPrimaryProduct(), firstDto.getAssociatedProduct(), 
                            firstDto.getCoOccurrenceCount(), firstDto.getConfidenceScore());
                } else {
                    log.error("DTO list is empty after conversion. Materialized had {} items", materialized.size());
                }
                
                BasketAnalysisResponse response = new BasketAnalysisResponse(dtos, false);
                log.info("Created BasketAnalysisResponse with {} DTOs", response.getItems() != null ? response.getItems().size() : 0);
                return response;
            }
        }

        // Try case-insensitive exact match using database query (more efficient)
        // Use repository method that queries by primary_product (indexed)
        caseInsensitiveMatchUnfiltered = basketAnalysisRepository
                .findByPrimaryProductIgnoreCaseOrderByCoOccurrenceCountDesc(normalizedProductName);
        log.info("Case-insensitive match (normalized) found {} items (unfiltered)", caseInsensitiveMatchUnfiltered.size());
        
        List<BasketAnalysis> caseInsensitiveMatch = caseInsensitiveMatchUnfiltered.stream()
                .filter(ba -> ba.getCoOccurrenceCount() >= MIN_CO_OCCURRENCE)
                .limit(TOP_K)
                .collect(Collectors.toList());
        log.info("Case-insensitive match (normalized) found {} items (filtered by threshold)", caseInsensitiveMatch.size());
        
        if (!caseInsensitiveMatchUnfiltered.isEmpty()) {
            log.debug("Sample case-insensitive match product names: {}", 
                    caseInsensitiveMatchUnfiltered.stream().limit(3).map(BasketAnalysis::getPrimaryProduct).collect(Collectors.toList()));
            log.debug("Sample co-occurrence counts: {}", 
                    caseInsensitiveMatchUnfiltered.stream().limit(3).map(BasketAnalysis::getCoOccurrenceCount).collect(Collectors.toList()));
        }
        
        if (!caseInsensitiveMatch.isEmpty()) {
            // Create new ArrayList copy to ensure entities are materialized
            List<BasketAnalysis> materialized = new ArrayList<>(caseInsensitiveMatch);
            // Force materialization of all entities
            for (BasketAnalysis ba : materialized) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
                ba.getConfidenceScore();
                ba.getSupportScore();
            }
            
            log.info("Created materialized list with {} items (original case-insensitive match had {})", 
                    materialized.size(), caseInsensitiveMatch.size());
            
            // Verify first entity is fully materialized
            if (!materialized.isEmpty()) {
                BasketAnalysis first = materialized.get(0);
                log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        first.getPrimaryProduct(), first.getAssociatedProduct(), 
                        first.getCoOccurrenceCount(), first.getConfidenceScore());
                if (first.getClass().getName().contains("HibernateProxy")) {
                    log.warn("WARNING: Entity is still a Hibernate proxy after materialization!");
                }
            }
            
            log.info("Returning {} items from case-insensitive match", materialized.size());
            
            // Convert entities to DTOs for serialization
            List<BasketAnalysisDTO> dtos = materialized.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            return new BasketAnalysisResponse(dtos, false);
        }
        
        // Fallback: if filtered is empty but we have unfiltered results, return top 10 unfiltered
        if (caseInsensitiveMatch.isEmpty() && !caseInsensitiveMatchUnfiltered.isEmpty()) {
            log.warn("All {} case-insensitive matches filtered out by threshold {}. Returning top {} unfiltered results.", 
                    caseInsensitiveMatchUnfiltered.size(), MIN_CO_OCCURRENCE, TOP_K);
            List<BasketAnalysis> unfiltered = caseInsensitiveMatchUnfiltered.stream()
                    .sorted((a, b) -> b.getCoOccurrenceCount() - a.getCoOccurrenceCount())
                    .limit(TOP_K)
                    .collect(Collectors.toList());
            
            // Create new ArrayList copy to ensure entities are materialized
            List<BasketAnalysis> materialized = new ArrayList<>(unfiltered);
            // Force materialization of all entities
            for (BasketAnalysis ba : materialized) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
                ba.getConfidenceScore();
                ba.getSupportScore();
            }
            
            log.info("Created materialized list with {} items (original unfiltered had {})", 
                    materialized.size(), unfiltered.size());
            
            // Verify first entity is fully materialized
            if (!materialized.isEmpty()) {
                BasketAnalysis first = materialized.get(0);
                log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        first.getPrimaryProduct(), first.getAssociatedProduct(), 
                        first.getCoOccurrenceCount(), first.getConfidenceScore());
                if (first.getClass().getName().contains("HibernateProxy")) {
                    log.warn("WARNING: Entity is still a Hibernate proxy after materialization!");
                }
            }
            
            log.info("Returning {} unfiltered items from case-insensitive match", materialized.size());
            
            // Convert entities to DTOs for serialization
            List<BasketAnalysisDTO> dtos = materialized.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            return new BasketAnalysisResponse(dtos, false);
        }

        // Try partial/fuzzy match using database query (more efficient)
        // Use repository method that queries with LIKE (indexed)
        // Use normalized product name for fuzzy matching
        partialMatchUnfiltered = basketAnalysisRepository
                .findByPrimaryProductContainingIgnoreCaseOrderByCoOccurrenceCountDesc(normalizedProductName)
                .stream()
                .filter(ba -> {
                    // Additional filtering: ensure it's a meaningful match
                    String normalizedProduct = normalizeProductName(ba.getPrimaryProduct()).toLowerCase();
                    String searchLower = normalizedProductName.toLowerCase();
                    return normalizedProduct.contains(searchLower) ||
                           searchLower.contains(normalizedProduct) ||
                           normalizedProduct.startsWith(searchLower) ||
                           searchLower.startsWith(normalizedProduct);
                })
                .collect(Collectors.toList());
        log.info("Fuzzy match (normalized) found {} items (unfiltered)", partialMatchUnfiltered.size());
        
        List<BasketAnalysis> partialMatch = partialMatchUnfiltered.stream()
                .filter(ba -> ba.getCoOccurrenceCount() >= MIN_CO_OCCURRENCE)
                .limit(TOP_K)
                .collect(Collectors.toList());
        log.info("Fuzzy match (normalized) found {} items (filtered by threshold)", partialMatch.size());
        
        if (!partialMatchUnfiltered.isEmpty()) {
            log.debug("Sample fuzzy match product names: {}", 
                    partialMatchUnfiltered.stream().limit(3).map(BasketAnalysis::getPrimaryProduct).collect(Collectors.toList()));
            log.debug("Sample co-occurrence counts: {}", 
                    partialMatchUnfiltered.stream().limit(3).map(BasketAnalysis::getCoOccurrenceCount).collect(Collectors.toList()));
        }
        
        if (!partialMatch.isEmpty()) {
            // Create new ArrayList copy to ensure entities are materialized
            List<BasketAnalysis> materialized = new ArrayList<>(partialMatch);
            // Force materialization of all entities
            for (BasketAnalysis ba : materialized) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
                ba.getConfidenceScore();
                ba.getSupportScore();
            }
            
            log.info("Created materialized list with {} items (original fuzzy match had {})", 
                    materialized.size(), partialMatch.size());
            
            // Verify first entity is fully materialized
            if (!materialized.isEmpty()) {
                BasketAnalysis first = materialized.get(0);
                log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        first.getPrimaryProduct(), first.getAssociatedProduct(), 
                        first.getCoOccurrenceCount(), first.getConfidenceScore());
                if (first.getClass().getName().contains("HibernateProxy")) {
                    log.warn("WARNING: Entity is still a Hibernate proxy after materialization!");
                }
            }
            
            log.info("Returning {} items from fuzzy match", materialized.size());
            
            // Convert entities to DTOs for serialization
            List<BasketAnalysisDTO> dtos = materialized.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            return new BasketAnalysisResponse(dtos, false);
        }
        
        // Fallback: if filtered is empty but we have unfiltered results, return top 10 unfiltered
        if (partialMatch.isEmpty() && !partialMatchUnfiltered.isEmpty()) {
            log.warn("All {} fuzzy matches filtered out by threshold {}. Returning top {} unfiltered results.", 
                    partialMatchUnfiltered.size(), MIN_CO_OCCURRENCE, TOP_K);
            List<BasketAnalysis> unfiltered = partialMatchUnfiltered.stream()
                    .sorted((a, b) -> b.getCoOccurrenceCount() - a.getCoOccurrenceCount())
                    .limit(TOP_K)
                    .collect(Collectors.toList());
            
            // Create new ArrayList copy to ensure entities are materialized
            List<BasketAnalysis> materialized = new ArrayList<>(unfiltered);
            // Force materialization of all entities
            for (BasketAnalysis ba : materialized) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
                ba.getConfidenceScore();
                ba.getSupportScore();
            }
            
            log.info("Created materialized list with {} items (original unfiltered had {})", 
                    materialized.size(), unfiltered.size());
            
            // Verify first entity is fully materialized
            if (!materialized.isEmpty()) {
                BasketAnalysis first = materialized.get(0);
                log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        first.getPrimaryProduct(), first.getAssociatedProduct(), 
                        first.getCoOccurrenceCount(), first.getConfidenceScore());
                if (first.getClass().getName().contains("HibernateProxy")) {
                    log.warn("WARNING: Entity is still a Hibernate proxy after materialization!");
                }
            }
            
            log.info("Returning {} unfiltered items from fuzzy match", materialized.size());
            
            // Convert entities to DTOs for serialization
            List<BasketAnalysisDTO> dtos = materialized.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            return new BasketAnalysisResponse(dtos, false);
        }

        // Try reverse lookup: find products where this product is an associated item
        reverseMatchUnfiltered = basketAnalysisRepository
                .findByAssociatedProductContainingIgnoreCaseOrderByCoOccurrenceCountDesc(normalizedProductName)
                .stream()
                .map(ba -> {
                    // Swap primary and associated for display
                    BasketAnalysis swapped = new BasketAnalysis();
                    swapped.setPrimaryProduct(productName);
                    swapped.setAssociatedProduct(ba.getPrimaryProduct());
                    swapped.setCoOccurrenceCount(ba.getCoOccurrenceCount());
                    swapped.setConfidenceScore(ba.getConfidenceScore());
                    swapped.setSupportScore(ba.getSupportScore());
                    return swapped;
                })
                .collect(Collectors.toList());
        log.info("Reverse lookup (normalized) found {} items (unfiltered)", reverseMatchUnfiltered.size());
        
        List<BasketAnalysis> reverseMatch = reverseMatchUnfiltered.stream()
                .filter(ba -> ba.getCoOccurrenceCount() >= MIN_CO_OCCURRENCE)
                .limit(TOP_K)
                .collect(Collectors.toList());
        log.info("Reverse lookup (normalized) found {} items (filtered by threshold)", reverseMatch.size());
        
        if (!reverseMatchUnfiltered.isEmpty()) {
            log.debug("Sample reverse match associated products: {}", 
                    reverseMatchUnfiltered.stream().limit(3).map(BasketAnalysis::getAssociatedProduct).collect(Collectors.toList()));
            log.debug("Sample co-occurrence counts: {}", 
                    reverseMatchUnfiltered.stream().limit(3).map(BasketAnalysis::getCoOccurrenceCount).collect(Collectors.toList()));
        }
        
        if (!reverseMatch.isEmpty()) {
            // Create new ArrayList copy to ensure entities are materialized
            List<BasketAnalysis> materialized = new ArrayList<>(reverseMatch);
            // Force materialization of all entities
            for (BasketAnalysis ba : materialized) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
                ba.getConfidenceScore();
                ba.getSupportScore();
            }
            
            log.info("Created materialized list with {} items (original reverse match had {})", 
                    materialized.size(), reverseMatch.size());
            
            // Verify first entity is fully materialized
            if (!materialized.isEmpty()) {
                BasketAnalysis first = materialized.get(0);
                log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        first.getPrimaryProduct(), first.getAssociatedProduct(), 
                        first.getCoOccurrenceCount(), first.getConfidenceScore());
                if (first.getClass().getName().contains("HibernateProxy")) {
                    log.warn("WARNING: Entity is still a Hibernate proxy after materialization!");
                }
            }
            
            log.info("Returning {} items from reverse lookup", materialized.size());
            
            // Convert entities to DTOs for serialization
            List<BasketAnalysisDTO> dtos = materialized.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            return new BasketAnalysisResponse(dtos, false);
        }
        
        // Fallback: if filtered is empty but we have unfiltered results, return top 10 unfiltered
        if (reverseMatch.isEmpty() && !reverseMatchUnfiltered.isEmpty()) {
            log.warn("All {} reverse matches filtered out by threshold {}. Returning top {} unfiltered results.", 
                    reverseMatchUnfiltered.size(), MIN_CO_OCCURRENCE, TOP_K);
            List<BasketAnalysis> unfiltered = reverseMatchUnfiltered.stream()
                    .sorted((a, b) -> b.getCoOccurrenceCount() - a.getCoOccurrenceCount())
                    .limit(TOP_K)
                    .collect(Collectors.toList());
            
            // Create new ArrayList copy to ensure entities are materialized
            List<BasketAnalysis> materialized = new ArrayList<>(unfiltered);
            // Force materialization of all entities
            for (BasketAnalysis ba : materialized) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
                ba.getConfidenceScore();
                ba.getSupportScore();
            }
            
            log.info("Created materialized list with {} items (original unfiltered had {})", 
                    materialized.size(), unfiltered.size());
            
            // Verify first entity is fully materialized
            if (!materialized.isEmpty()) {
                BasketAnalysis first = materialized.get(0);
                log.debug("First materialized entity - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        first.getPrimaryProduct(), first.getAssociatedProduct(), 
                        first.getCoOccurrenceCount(), first.getConfidenceScore());
                if (first.getClass().getName().contains("HibernateProxy")) {
                    log.warn("WARNING: Entity is still a Hibernate proxy after materialization!");
                }
            }
            
            log.info("Returning {} unfiltered items from reverse lookup", materialized.size());
            
            // Convert entities to DTOs for serialization
            List<BasketAnalysisDTO> dtos = materialized.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            return new BasketAnalysisResponse(dtos, false);
        }

        // If no matches found, check if this is a new product and generate predictions
        log.info("No matches found from any search strategy, checking if product is new...");
        if (newProductPredictionService.isNewProduct(productName)) {
            List<BasketAnalysis> predictions = newProductPredictionService.generatePredictedAssociations(productName);
            // Force eager loading for predictions
            for (BasketAnalysis ba : predictions) {
                ba.getAssociatedProduct();
                ba.getCoOccurrenceCount();
            }
            log.info("Generated {} predicted associations for new product", predictions.size());
            
            // Convert predicted entities to DTOs for serialization
            // Note: predictions are already plain Java objects (not JPA entities), but we convert to DTOs for consistency
            List<BasketAnalysisDTO> dtos = predictions.stream()
                    .map(BasketAnalysisDTO::from)
                    .collect(Collectors.toList());
            
            log.info("Converted {} predicted entities to DTOs. DTO list size: {}", predictions.size(), dtos.size());
            if (!dtos.isEmpty()) {
                BasketAnalysisDTO firstDto = dtos.get(0);
                log.info("First predicted DTO - Primary: '{}', Associated: '{}', Count: {}, Confidence: {}", 
                        firstDto.getPrimaryProduct(), firstDto.getAssociatedProduct(), 
                        firstDto.getCoOccurrenceCount(), firstDto.getConfidenceScore());
            } else {
                log.error("Predicted DTO list is empty after conversion. Predictions had {} items", predictions.size());
            }
            
            BasketAnalysisResponse response = new BasketAnalysisResponse(dtos, true);
            log.info("Created BasketAnalysisResponse with {} predicted DTOs", response.getItems() != null ? response.getItems().size() : 0);
            return response;
        }

        // No data found for this product (not new, but no matches)
        log.warn("No frequently bought together items found for product: '{}' (normalized: '{}'). Total basket analyses in DB: {}", 
                productName, normalizedProductName, totalCount);
        log.warn("All search attempts returned empty - exact: {}, case-insensitive: {}, fuzzy: {}, reverse: {}", 
                exactMatch.size(), 
                caseInsensitiveMatchUnfiltered.size(),
                partialMatchUnfiltered.size(),
                reverseMatchUnfiltered.size());
        return new BasketAnalysisResponse(new ArrayList<>(), false);
    }

    /**
     * Normalize product name for consistent matching.
     * Handles apostrophe variations and whitespace normalization.
     * 
     * @param productName Original product name
     * @return Normalized product name
     */
    private String normalizeProductName(String productName) {
        if (productName == null) {
            return "";
        }
        
        // Normalize apostrophes: U+0027 (straight), U+2019 (right single quote), U+2018 (left single quote) → U+0027
        String normalized = productName
                .replace('\u2019', '\'')  // Right single quotation mark
                .replace('\u2018', '\'')  // Left single quotation mark
                .replace('\u201C', '"')   // Left double quotation mark
                .replace('\u201D', '"')   // Right double quotation mark
                .trim();
        
        // Remove extra whitespace
        normalized = normalized.replaceAll("\\s+", " ");
        
        return normalized;
    }

    /**
     * Get frequently bought together items with minimum confidence filter.
     * Still applies 25+ co-occurrence threshold, then filters by confidence.
     * 
     * NOTE: Transaction is managed at the controller level to keep session open during serialization.
     * 
     * If all items are filtered out by the confidence threshold, returns the original response
     * to prevent data loss. This is user-friendly: better to show some data than no data.
     */
    public BasketAnalysisResponse getFrequentlyBoughtTogether(String productName, double minConfidence) {
        // Validate input
        if (productName == null || productName.trim().isEmpty()) {
            log.warn("Invalid product name provided for confidence filtering: '{}'", productName);
            return new BasketAnalysisResponse(new ArrayList<>(), false);
        }
        
        // Get base response (without confidence filter)
        BasketAnalysisResponse response = getFrequentlyBoughtTogether(productName);
        
        // Early return for empty, null, or predicted responses
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            log.debug("Base response is empty or null, returning as-is");
            return response != null ? response : new BasketAnalysisResponse(new ArrayList<>(), false);
        }
        
        // Predicted responses should not be filtered by confidence (they're synthetic)
        if (response.isPredicted()) {
            log.debug("Response is predicted, skipping confidence filter");
            return response;
        }
        
        // Apply confidence filter with null safety
        log.info("Filtering {} items by confidence >= {}", response.getItems().size(), minConfidence);
        
        List<BasketAnalysisDTO> filtered = response.getItems().stream()
                .filter(ba -> {
                    // Null safety: skip items with null confidence scores
                    if (ba == null || ba.getConfidenceScore() == null) {
                        log.warn("Skipping item with null confidence score: {}", ba != null ? ba.getAssociatedProduct() : "null");
                        return false;
                    }
                    return ba.getConfidenceScore().doubleValue() >= minConfidence;
                })
                .sorted((a, b) -> {
                    // Sort by co-occurrence count descending, then by confidence descending
                    int countDiff = b.getCoOccurrenceCount() - a.getCoOccurrenceCount();
                    if (countDiff != 0) return countDiff;
                    // Secondary sort by confidence if counts are equal
                    double confDiff = b.getConfidenceScore().doubleValue() - a.getConfidenceScore().doubleValue();
                    return confDiff > 0 ? 1 : (confDiff < 0 ? -1 : 0);
                })
                .collect(Collectors.toList());
        
        log.info("Filtered result: {} items remaining (from {} original)", filtered.size(), response.getItems().size());
        
        // If all items filtered out, return original response instead of empty
        // This prevents losing all data when confidence threshold is too high
        // This is user-friendly: better to show some data than no data
        if (filtered.isEmpty() && !response.getItems().isEmpty()) {
            log.warn("All {} items filtered out by confidence threshold {}. " +
                    "Confidence scores range from {} to {}. Returning original response with all items to prevent data loss.",
                    response.getItems().size(), 
                    minConfidence,
                    response.getItems().stream()
                        .filter(ba -> ba != null && ba.getConfidenceScore() != null)
                        .mapToDouble(ba -> ba.getConfidenceScore().doubleValue())
                        .min()
                        .orElse(0.0),
                    response.getItems().stream()
                        .filter(ba -> ba != null && ba.getConfidenceScore() != null)
                        .mapToDouble(ba -> ba.getConfidenceScore().doubleValue())
                        .max()
                        .orElse(0.0));
            return response; // Return original instead of empty
        }
        
        // If we have filtered items, return filtered response
        return new BasketAnalysisResponse(filtered, response.isPredicted());
    }

    // Removed ensureTop10 method - no longer needed with simplified 25+ threshold approach

    /**
     * Count total number of basket analysis records.
     * Used to check if any data exists globally.
     */
    public long countAll() {
        return basketAnalysisRepository.count();
    }

    /**
     * Recalculate basket analysis from transaction data.
     * This implements a simple association rule mining algorithm.
     */
    @Transactional
    public void recalculateBasketAnalysis(LocalDate startDate, LocalDate endDate) {
        // Get all transactions in the date range
        List<Transaction> transactions = transactionRepository.findByTransactionDateBetween(startDate, endDate);

        if (transactions.isEmpty()) {
            return;
        }

        // Build product co-occurrence map: product -> {other product -> count}
        Map<String, Map<String, Integer>> coOccurrenceMap = new HashMap<>();
        Map<String, Integer> productFrequency = new HashMap<>();
        int totalTransactions = transactions.size();

        for (Transaction transaction : transactions) {
            Set<String> productsInTransaction = transaction.getItems().stream()
                    .map(TransactionItem::getProductName)
                    .collect(Collectors.toSet());

            // Update product frequency
            for (String product : productsInTransaction) {
                productFrequency.put(product, productFrequency.getOrDefault(product, 0) + 1);
            }

            // Update co-occurrence counts
            List<String> productList = new ArrayList<>(productsInTransaction);
            for (int i = 0; i < productList.size(); i++) {
                String product1 = productList.get(i);
                coOccurrenceMap.putIfAbsent(product1, new HashMap<>());

                for (int j = i + 1; j < productList.size(); j++) {
                    String product2 = productList.get(j);

                    // Count both directions
                    coOccurrenceMap.get(product1).put(
                            product2,
                            coOccurrenceMap.get(product1).getOrDefault(product2, 0) + 1);

                    coOccurrenceMap.putIfAbsent(product2, new HashMap<>());
                    coOccurrenceMap.get(product2).put(
                            product1,
                            coOccurrenceMap.get(product2).getOrDefault(product1, 0) + 1);
                }
            }
        }

        // Calculate confidence and support scores, then save
        List<BasketAnalysis> analyses = new ArrayList<>();

        for (Map.Entry<String, Map<String, Integer>> entry : coOccurrenceMap.entrySet()) {
            String primaryProduct = entry.getKey();
            int primaryFreq = productFrequency.getOrDefault(primaryProduct, 1);

            for (Map.Entry<String, Integer> coOccEntry : entry.getValue().entrySet()) {
                String associatedProduct = coOccEntry.getKey();
                int coOccCount = coOccEntry.getValue();

                // Save all associations (removed strict confidence threshold to show more data)
                // Only require at least 1 co-occurrence
                // This ensures we capture ALL co-occurrences to build comprehensive top 10 lists
                if (coOccCount > 0) {
                    // Find existing record by both primary and associated product
                    // This properly handles the UNIQUE constraint on (primary_product, associated_product)
                    BasketAnalysis analysis = basketAnalysisRepository
                            .findByPrimaryProductAndAssociatedProduct(primaryProduct, associatedProduct)
                            .orElse(new BasketAnalysis());
            
                    // Update or set fields
                    analysis.setPrimaryProduct(primaryProduct);
                    analysis.setAssociatedProduct(associatedProduct);
                    
                    // Since we process ALL transactions in the date range at once,
                    // we REPLACE the count (not accumulate). The coOccCount is already
                    // the total count for all transactions in this date range.
                    analysis.setCoOccurrenceCount(coOccCount);
                    
                    // Calculate confidence and support based on the count
                    // Support: P(A and B) = transactions containing both / total transactions
                    BigDecimal support = BigDecimal.valueOf(coOccCount)
                            .divide(BigDecimal.valueOf(totalTransactions), 4, RoundingMode.HALF_UP);

                    // Confidence: P(B|A) = transactions containing both / transactions containing A
                    BigDecimal confidence = BigDecimal.valueOf(coOccCount)
                            .divide(BigDecimal.valueOf(primaryFreq), 4, RoundingMode.HALF_UP);
                    
                    analysis.setConfidenceScore(confidence);
                    analysis.setSupportScore(support);
            
                    analyses.add(analysis);
                }
            }
        }

        // Save all analyses (this will create/update records)
        // Note: JPA saveAll will update existing records if they have the same primary key
        // or create new ones if they don't exist
        basketAnalysisRepository.saveAll(analyses);
    }
}
