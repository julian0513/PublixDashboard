package com.julian.publixai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ForecastResponse
 *
 * Purpose: Container for a forecast query result across a date range.
 *
 * Fields:
 * - startDate  : inclusive range start as ISO-8601 (YYYY-MM-DD)
 * - endDate    : inclusive range end as ISO-8601 (YYYY-MM-DD)
 * - generatedAt: server-side timestamp (ISO-8601 with offset) indicating when this payload was produced
 * - items      : ordered list of per-product forecasts (see ForecastItem)
 *
 * Notes:
 * - JSON omits null fields for compactness.
 * - Date/time formatting is explicit for predictable clients and tests.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForecastResponse {

    private String startDate;
    private String endDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime generatedAt;

    private List<ForecastItem> items;

    public ForecastResponse() {
        // no-arg ctor for Jackson
    }

    public ForecastResponse(String startDate, String endDate, OffsetDateTime generatedAt, List<ForecastItem> items) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.generatedAt = generatedAt;
        this.items = items;
    }

    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public List<ForecastItem> getItems() { return items; }

    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
    public void setItems(List<ForecastItem> items) { this.items = items; }
}
