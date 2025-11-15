package com.julian.publixai.dto;

import com.julian.publixai.model.DiscountEffectiveness;
import java.math.BigDecimal;
import java.util.List;

public class DiscountEffectivenessResponse {
    private List<DiscountEffectiveness> items;
    private boolean isPredicted;
    private BigDecimal optimalDiscountPercent;
    
    public DiscountEffectivenessResponse() {}
    
    public DiscountEffectivenessResponse(List<DiscountEffectiveness> items, boolean isPredicted, BigDecimal optimalDiscountPercent) {
        this.items = items;
        this.isPredicted = isPredicted;
        this.optimalDiscountPercent = optimalDiscountPercent;
    }
    
    public List<DiscountEffectiveness> getItems() {
        return items;
    }
    
    public void setItems(List<DiscountEffectiveness> items) {
        this.items = items;
    }
    
    public boolean isPredicted() {
        return isPredicted;
    }
    
    public void setPredicted(boolean predicted) {
        isPredicted = predicted;
    }
    
    public BigDecimal getOptimalDiscountPercent() {
        return optimalDiscountPercent;
    }
    
    public void setOptimalDiscountPercent(BigDecimal optimalDiscountPercent) {
        this.optimalDiscountPercent = optimalDiscountPercent;
    }
}

