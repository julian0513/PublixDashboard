package com.julian.publixai.controller;

import com.julian.publixai.service.MlClient;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.constraints.Pattern;

/**
 * ML admin endpoints (training).
 * Uses explicit @RequestParam name so we do not depend on the '-parameters' flag.
 */
@RestController
@RequestMapping("/api/ml")
@Validated
public class MlAdminController {

    private final MlClient mlClient;

    public MlAdminController(MlClient mlClient) {
        this.mlClient = mlClient;
    }

    /**
     * Trigger training for either "seed" or "live".
     * Example: POST /api/ml/train?mode=live
     */
    @PostMapping("/train")
    public Object train(
            @RequestParam(name = "mode", defaultValue = "live")
            @Pattern(regexp = "seed|live", message = "mode must be 'seed' or 'live'")
            String mode
    ) {
        try {
            // Delegate to the client; return whatever the ML service returns.
            return mlClient.train(mode);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to call ML service for training",
                    e
            );
        }
    }
}
