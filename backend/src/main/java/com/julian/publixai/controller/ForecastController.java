package com.julian.publixai.controller;

import com.julian.publixai.dto.ForecastResponse;
import com.julian.publixai.service.ForecastService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Forecast API (GET /api/forecast)
 *
 * Purpose:
 * - Serve sales forecasts for a given date range using either the frozen "seed" model
 *   or the updated "live" model.
 *
 * Notes:
 * - Explicit @RequestParam names are used to avoid reliance on the Java -parameters flag.
 * - Dates are parsed as ISO-8601 (yyyy-MM-dd), matching frontend usage.
 * - Basic input validation keeps the surface area predictable and safe.
 *
 * Example:
 *   GET /api/forecast?start=2025-10-01&end=2025-10-31&topK=10&mode=seed
 */
@RestController
@RequestMapping("/api")
@Validated
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    /**
     * Returns a forecast for the provided date window.
     *
     * @param start Start date (inclusive), ISO yyyy-MM-dd
     * @param end   End date (inclusive), ISO yyyy-MM-dd
     * @param topK  Number of top products to return (1–100). Default: 10.
     * @param mode  Forecast mode: "seed" (frozen baseline) or "live" (updated model). Default: "seed".
     */
    @GetMapping("/forecast")
    public ForecastResponse getForecast(
            @RequestParam("start")
            @DateTimeFormat(iso = ISO.DATE)
            LocalDate start,

            @RequestParam("end")
            @DateTimeFormat(iso = ISO.DATE)
            LocalDate end,

            @RequestParam(name = "topK", defaultValue = "10")
            @Min(1) @Max(100)
            int topK,

            @RequestParam(name = "mode", defaultValue = "seed")
            @Pattern(regexp = "seed|live", message = "mode must be 'seed' or 'live'")
            String mode
    ) {
        // Defensive check: start must be on or before end
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be on or before end");
        }

        // Delegate to service layer
        return forecastService.getForecast(start, end, topK, mode);
    }
}
