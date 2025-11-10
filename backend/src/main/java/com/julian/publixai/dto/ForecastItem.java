package com.julian.publixai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ForecastItem
 *
 * Purpose: Represents a single product's forecast in the response payload.
 *
 * Fields:
 * - productName      : display name of the SKU/product.
 * - predictedUnits   : total predicted units over the requested date range.
 * - confidence (0..1): model confidence score (1 = highest). Frontend formats as a percentage.
 *
 * Notes:
 * - Kept as a standard Java bean (constructors + getters/setters) to avoid breaking existing code paths.
 * - JSON omits nulls (future-proof if a field becomes optional later).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForecastItem {

    private String productName;
    private double predictedUnits;
    private double confidence; // expected in [0, 1]

    public ForecastItem() {
        // no-arg ctor for Jackson
    }

    public ForecastItem(String productName, double predictedUnits, double confidence) {
        this.productName = productName;
        this.predictedUnits = predictedUnits;
        this.confidence = confidence;
    }

    public String getProductName() {
        return productName;
    }

    public double getPredictedUnits() {
        return predictedUnits;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setPredictedUnits(double predictedUnits) {
        this.predictedUnits = predictedUnits;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
