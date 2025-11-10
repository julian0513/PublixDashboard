package com.julian.publixai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MlProperties
 *
 * Purpose: Typed configuration for the Python ML (Machine Learning) service.
 *
 * Notes:
 * - Values are bound from properties with prefix "ml".
 * - Safe dev defaults are provided; production should override via env.
 * - Timeout is range-checked to prevent unsafe extremes.
 */
@Validated
@ConfigurationProperties(prefix = "ml")
public class MlProperties {

    /**
     * Base URL of the ML FastAPI service.
     * e.g., http://localhost:8000 (dev) or http://ml:8000 (docker)
     */
    @NotBlank
    private String baseUrl = "http://localhost:8000";

    /**
     * Shared secret sent as X-ML-Secret on each request.
     * In production, set via environment variable ML_SECRET.
     */
    @NotBlank
    private String secret = "dev-secret";

    /**
     * Network timeout (milliseconds) applied to connect/read/write.
     * Clamped by validation to 500–30000 ms.
     */
    @Min(500)
    @Max(30_000)
    private int timeoutMs = 3_000;

    // ---- Getters / Setters ----

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
