package com.julian.publixai.repository;

import com.julian.publixai.model.BasketAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BasketAnalysisRepository extends JpaRepository<BasketAnalysis, UUID> {

    List<BasketAnalysis> findByPrimaryProductOrderByConfidenceScoreDesc(String primaryProduct);

    @Query("SELECT ba FROM BasketAnalysis ba WHERE ba.primaryProduct = :productName ORDER BY ba.confidenceScore DESC")
    List<BasketAnalysis> findTopAssociationsForProduct(@Param("productName") String productName);

    @Query("SELECT ba FROM BasketAnalysis ba WHERE ba.primaryProduct = :productName AND ba.confidenceScore >= :minConfidence ORDER BY ba.confidenceScore DESC")
    List<BasketAnalysis> findAssociationsWithMinConfidence(@Param("productName") String productName, @Param("minConfidence") double minConfidence);

    /**
     * Find by primary product (case-insensitive) ordered by co-occurrence count (descending).
     * Uses database-level filtering for better performance.
     */
    @Query("SELECT ba FROM BasketAnalysis ba WHERE LOWER(ba.primaryProduct) = LOWER(:primaryProduct) ORDER BY ba.coOccurrenceCount DESC")
    List<BasketAnalysis> findByPrimaryProductIgnoreCaseOrderByCoOccurrenceCountDesc(@Param("primaryProduct") String primaryProduct);

    /**
     * Find by primary product containing search term (case-insensitive) ordered by co-occurrence count.
     * Used for fuzzy matching with database-level filtering.
     */
    @Query("SELECT ba FROM BasketAnalysis ba WHERE LOWER(ba.primaryProduct) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY ba.coOccurrenceCount DESC")
    List<BasketAnalysis> findByPrimaryProductContainingIgnoreCaseOrderByCoOccurrenceCountDesc(@Param("searchTerm") String searchTerm);

    /**
     * Find a specific basket analysis record by primary and associated product.
     * Used to check if a relationship already exists before creating/updating.
     */
    @Query("SELECT ba FROM BasketAnalysis ba WHERE ba.primaryProduct = :primaryProduct AND ba.associatedProduct = :associatedProduct")
    java.util.Optional<BasketAnalysis> findByPrimaryProductAndAssociatedProduct(
            @Param("primaryProduct") String primaryProduct,
            @Param("associatedProduct") String associatedProduct);

    /**
     * Find by associated product containing search term (case-insensitive) ordered by co-occurrence count.
     * Used for reverse lookup when primary product search fails.
     */
    @Query("SELECT ba FROM BasketAnalysis ba WHERE LOWER(ba.associatedProduct) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY ba.coOccurrenceCount DESC")
    List<BasketAnalysis> findByAssociatedProductContainingIgnoreCaseOrderByCoOccurrenceCountDesc(@Param("searchTerm") String searchTerm);
}

