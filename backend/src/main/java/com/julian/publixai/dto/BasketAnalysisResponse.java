package com.julian.publixai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * BasketAnalysisResponse
 * 
 * Data Transfer Object for frequently bought together items API responses.
 * Encapsulates the list of basket analysis items and a flag indicating whether
 * the data is predicted (for new products) or historical.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BasketAnalysisResponse {
    private List<BasketAnalysisDTO> items;
    
    @JsonProperty("isPredicted")
    private boolean isPredicted;
    
    public BasketAnalysisResponse() {}
    
    /**
     * Constructs a BasketAnalysisResponse with the provided items and prediction flag.
     * 
     * @param items List of basket analysis DTOs (null-safe, defaults to empty list)
     * @param isPredicted True if data is predicted for new products, false for historical data
     */
    public BasketAnalysisResponse(List<BasketAnalysisDTO> items, boolean isPredicted) {
        this.items = items != null ? items : new java.util.ArrayList<>();
        this.isPredicted = isPredicted;
    }
    
    public List<BasketAnalysisDTO> getItems() {
        return items;
    }
    
    public void setItems(List<BasketAnalysisDTO> items) {
        this.items = items;
    }
    
    public boolean isPredicted() {
        return isPredicted;
    }
    
    public void setPredicted(boolean predicted) {
        isPredicted = predicted;
    }
}

