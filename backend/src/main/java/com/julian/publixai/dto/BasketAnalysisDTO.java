package com.julian.publixai.dto;

import com.julian.publixai.model.BasketAnalysis;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * BasketAnalysisDTO
 * 
 * Purpose: Plain DTO for BasketAnalysis data, used for JSON serialization.
 * This ensures Jackson serializes plain Java objects instead of JPA entities,
 * preventing serialization issues with detached entities.
 */
public class BasketAnalysisDTO {
    private UUID id;
    private String primaryProduct;
    private String associatedProduct;
    private int coOccurrenceCount;
    private BigDecimal confidenceScore;
    private BigDecimal supportScore;
    
    // No-arg constructor for Jackson
    public BasketAnalysisDTO() {}
    
    // Full constructor
    public BasketAnalysisDTO(UUID id, String primaryProduct, String associatedProduct, 
                            int coOccurrenceCount, BigDecimal confidenceScore, BigDecimal supportScore) {
        this.id = id;
        this.primaryProduct = primaryProduct;
        this.associatedProduct = associatedProduct;
        this.coOccurrenceCount = coOccurrenceCount;
        this.confidenceScore = confidenceScore;
        this.supportScore = supportScore;
    }
    
    /**
     * Factory method to convert a BasketAnalysis JPA entity to a plain DTO.
     * This conversion is necessary to prevent Jackson serialization issues
     * with detached Hibernate entities.
     * 
     * @param entity The JPA entity to convert
     * @return BasketAnalysisDTO instance, or null if entity is null or conversion fails
     */
    public static BasketAnalysisDTO from(BasketAnalysis entity) {
        if (entity == null) {
            return null;
        }
        try {
            return new BasketAnalysisDTO(
                entity.getId(),
                entity.getPrimaryProduct(),
                entity.getAssociatedProduct(),
                entity.getCoOccurrenceCount(),
                entity.getConfidenceScore(),
                entity.getSupportScore()
            );
        } catch (Exception e) {
            // Return null on conversion failure - error handling should be done at service layer
            return null;
        }
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getPrimaryProduct() {
        return primaryProduct;
    }
    
    public void setPrimaryProduct(String primaryProduct) {
        this.primaryProduct = primaryProduct;
    }
    
    public String getAssociatedProduct() {
        return associatedProduct;
    }
    
    public void setAssociatedProduct(String associatedProduct) {
        this.associatedProduct = associatedProduct;
    }
    
    public int getCoOccurrenceCount() {
        return coOccurrenceCount;
    }
    
    public void setCoOccurrenceCount(int coOccurrenceCount) {
        this.coOccurrenceCount = coOccurrenceCount;
    }
    
    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }
    
    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
    
    public BigDecimal getSupportScore() {
        return supportScore;
    }
    
    public void setSupportScore(BigDecimal supportScore) {
        this.supportScore = supportScore;
    }
}

