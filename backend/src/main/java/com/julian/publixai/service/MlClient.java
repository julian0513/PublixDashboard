package com.julian.publixai.service;

import com.julian.publixai.dto.ForecastResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * MlClient
 *
 * Purpose: Single integration point to the Python FastAPI ML service.
 *
 * Endpoints:
 * - GET  /ml/forecast?start=YYYY-MM-DD&end=YYYY-MM-DD&mode=seed|live&top_k=N
 * - POST /ml/train?mode=seed|live
 *
 * Notes:
 * - Mode normalization supports legacy aliases (historical→seed, auto/nowcast→live).
 * - Timeouts and secret header are configured in AppConfig's mlWebClient().
 * - 5xx errors are already transformed by AppConfig filter; here we add body capture for 4xx.
 */
@Service
public class MlClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient mlWebClient;

    public MlClient(@Qualifier("mlWebClient") WebClient mlWebClient) {
        this.mlWebClient = Objects.requireNonNull(mlWebClient, "mlWebClient");
    }

    /**
     * Fetch a forecast from the ML service.
     */
    public ForecastResponse forecast(LocalDate start, LocalDate end, String mode, Integer topK) {
        String m = normalizeMode(mode);

        try {
            return mlWebClient.get()
                    .uri(uri -> {
                        var b = uri.path("/ml/forecast")
                                .queryParam("start", start)
                                .queryParam("end", end)
                                .queryParam("mode", m);
                        if (topK != null) {
                            b.queryParam("top_k", topK);
                        }
                        return b.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(ForecastResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            // Attach response body for easier troubleshooting (4xx etc.)
            throw toMlClientException("forecast", e);
        }
    }

    /**
     * Trigger model training on the ML service and return metadata (e.g., model path, counts).
     */
    public Map<String, Object> train(String mode) {
        String m = normalizeMode(mode);

        try {
            return mlWebClient.post()
                    .uri(uri -> uri.path("/ml/train").queryParam("mode", m).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block();
        } catch (WebClientResponseException e) {
            throw toMlClientException("train", e);
        } catch (Exception e) {
            // Catch all other exceptions (timeouts, connection errors, etc.)
            throw new IllegalStateException("ML service training failed: " + e.getMessage(), e);
        }
    }

    // ---------- Helpers ----------

    /**
     * Normalize legacy mode aliases to the two supported values.
     */
    private static String normalizeMode(String mode) {
        String v = (mode == null ? "live" : mode.trim().toLowerCase());
        return switch (v) {
            case "seed", "live" -> v;
            case "historical", "baseline" -> "seed";
            case "auto", "updated", "nowcast" -> "live";
            default -> throw new IllegalArgumentException("mode must be 'seed' or 'live'");
        };
    }

    private static RuntimeException toMlClientException(String op, WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        String msg = "%s failed: %d %s".formatted(op, e.getStatusCode().value(), e.getStatusText());
        if (body != null && !body.isBlank()) {
            msg += " body=" + body;
        }
        return new IllegalStateException(msg, e);
    }
}
