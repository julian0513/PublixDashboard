package com.julian.publixai.service;

import com.julian.publixai.dto.ForecastResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

/**
 * BaselineService
 *
 * Purpose: Compatibility wrapper for the historical baseline forecast.
 * ALL predictions (including the "baseline") come
 * from the seed scikit-learn model (history_seed.joblib) via MlClient.
 *
 * Guarantees:
 * - Delegates to MlClient with mode="seed".
 * - Validates date range (end >= start).
 * - Returns a typed ForecastResponse suitable for the frontend.
 */
@Service
public class BaselineService {

    private final MlClient mlClient;

    public BaselineService(MlClient mlClient) {
        this.mlClient = Objects.requireNonNull(mlClient, "mlClient");
    }

    /**
     * Produce a baseline forecast by calling the seed model.
     *
     * @param start inclusive start date (YYYY-MM-DD)
     * @param end   inclusive end date (YYYY-MM-DD)
     * @param topK  optional cap for number of items (null → no cap)
     * @return ForecastResponse from the ML service (mode=seed)
     * @throws IllegalArgumentException if end is before start
     */
    public ForecastResponse baseline(LocalDate start, LocalDate end, Integer topK) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end date must be on or after start date");
        }
        // Delegate to ML service "seed" mode (frozen baseline model)
        return mlClient.forecast(start, end, "seed", topK);
    }
}
