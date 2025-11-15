package com.julian.publixai.controller;

import com.julian.publixai.service.HistoricalDataGeneratorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HistoricalDataController
 * 
 * Endpoint to generate synthetic discount and transaction data from historical CSV.
 */
@RestController
@RequestMapping("/api/historical")
public class HistoricalDataController {

    private final HistoricalDataGeneratorService historicalDataGeneratorService;

    public HistoricalDataController(HistoricalDataGeneratorService historicalDataGeneratorService) {
        this.historicalDataGeneratorService = historicalDataGeneratorService;
    }

    /**
     * POST /api/historical/generate
     * Generate synthetic discount and transaction data from historical CSV.
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.OK)
    public void generateHistoricalData() {
        historicalDataGeneratorService.generateFromHistoricalCsv();
    }
}

