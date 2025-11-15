package com.julian.publixai.service;

import com.julian.publixai.dto.ForecastItem;
import com.julian.publixai.dto.ForecastResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ForecastService
 *
 * - Delegates to MlClient (seed|live) then normalizes output for the UI.
 * - Ensures each product's predictedUnits >= actuals within [start,end].
 * - Adds any products that have actuals but were missing from model output.
 */
@Service
public class ForecastService {

    private static final int MAX_TOP_K = 100;

    private final MlClient mlClient;
    private final SalesStatsService salesStats;

    public ForecastService(MlClient mlClient, SalesStatsService salesStats) {
        this.mlClient = mlClient;
        this.salesStats = salesStats;
    }

    public ForecastResponse getForecast(LocalDate start, LocalDate end, int topK, String mode) {
        final int k = Math.max(1, Math.min(topK, MAX_TOP_K));

        final ForecastResponse raw;
        try {
            raw = mlClient.forecast(start, end, mode, k);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch forecast from ML service", e);
        }

        // Ensure stable shell/meta
        final ForecastResponse out = (raw != null) ? raw : new ForecastResponse();
        if (out.getStartDate() == null) out.setStartDate(start.toString());
        if (out.getEndDate() == null) out.setEndDate(end.toString());
        if (out.getGeneratedAt() == null) out.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // Step 1: clean + rank model items
        List<ForecastItem> items = cleanAndRank(out.getItems(), k);

        // Step 2: apply "actuals floor" for the requested window
        Map<String, Long> actuals = salesStats.actualsByProductBetween(start, end);
        if (actuals != null && !actuals.isEmpty()) {
            Map<String, ForecastItem> byName = items.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            ForecastItem::getProductName,
                            fi -> fi,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));

            for (Map.Entry<String, Long> e : actuals.entrySet()) {
                String name = e.getKey();
                double actual = e.getValue() == null ? 0.0 : e.getValue().doubleValue();
                if (actual <= 0) continue;

                ForecastItem fi = byName.get(name);
                if (fi == null) {
                    fi = new ForecastItem();
                    fi.setProductName(name);
                    fi.setPredictedUnits(actual); // add missing product with actuals
                    items.add(fi);
                } else {
                    Double pred = fi.getPredictedUnits();
                    fi.setPredictedUnits(Math.max(pred == null ? 0.0 : pred, actual)); // floor
                }
            }

            // Re-rank & trim after applying floors/new items
            items = items.stream()
                    .filter(Objects::nonNull)
                    .map(ForecastService::clipNegative)
                    .sorted(Comparator.comparing(
                            ForecastItem::getPredictedUnits,
                            Comparator.nullsFirst(Double::compareTo)
                    ).reversed())
                    .limit(k)
                    .collect(Collectors.toList());
        }

        out.setItems(items);
        return out;
    }

    // ---------- helpers ----------

    private static List<ForecastItem> cleanAndRank(List<?> items, int topK) {
        if (items == null || items.isEmpty()) return List.of();

        return items.stream()
                .filter(Objects::nonNull)
                .map(ForecastService::toForecastItem)
                .filter(Objects::nonNull)
                .map(ForecastService::clipNegative)
                .sorted(Comparator.comparing(
                        ForecastItem::getPredictedUnits,
                        Comparator.nullsFirst(Double::compareTo)
                ).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private static ForecastItem toForecastItem(Object o) {
        if (o instanceof ForecastItem fi) return fi;
        if (o instanceof Map<?, ?> m) {
            ForecastItem fi = new ForecastItem();
            Object name = m.get("productName");
            Object pred = m.get("predictedUnits");
            if (name != null) fi.setProductName(String.valueOf(name));
            if (pred instanceof Number n) fi.setPredictedUnits(n.doubleValue());
            else if (pred != null) {
                try { fi.setPredictedUnits(Double.parseDouble(pred.toString())); }
                catch (NumberFormatException ex) { fi.setPredictedUnits(0.0); }
            }
            return fi;
        }
        return null;
    }

    private static ForecastItem clipNegative(ForecastItem item) {
        Double v = item.getPredictedUnits();
        if (v == null || v < 0.0) item.setPredictedUnits(0.0);
        return item;
    }
}
