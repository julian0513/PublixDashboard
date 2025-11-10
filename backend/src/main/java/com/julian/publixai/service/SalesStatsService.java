package com.julian.publixai.service;

import com.julian.publixai.repository.SaleRepository;
import com.julian.publixai.repository.SaleRepository.SumRow;
import com.julian.publixai.repository.SaleRepository.AvgRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates sales stats with stable ordering (desc by value).
 */
@Service
public class SalesStatsService {

    private final SaleRepository saleRepository;

    public SalesStatsService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    /** Actuals summed by product for the range. */
    @Transactional(readOnly = true)
    public Map<String, Long> actualsByProductBetween(LocalDate start, LocalDate end) {
        List<SumRow> rows = saleRepository.sumByProductBetween(start, end);
        Map<String, Long> out = new LinkedHashMap<>();
        for (SumRow r : rows) {
            out.put(r.getProductName(), r.getUnits() == null ? 0L : r.getUnits());
        }
        return out;
    }

    /** Average per-day units by product for the range. */
    @Transactional(readOnly = true)
    public Map<String, Double> avgPerDayByProductBetween(LocalDate start, LocalDate end) {
        List<AvgRow> rows = saleRepository.avgPerDayByProductBetween(start, end);
        Map<String, Double> out = new LinkedHashMap<>();
        for (AvgRow r : rows) {
            out.put(r.getProductName(), r.getAvgUnits() == null ? 0.0 : r.getAvgUnits());
        }
        return out;
    }
}
