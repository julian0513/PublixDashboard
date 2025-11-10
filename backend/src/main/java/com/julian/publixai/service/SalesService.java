package com.julian.publixai.service;

import com.julian.publixai.model.SaleRecord;
import com.julian.publixai.repository.SaleRepository;
import com.julian.publixai.repository.SalesChangeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SalesService
 * - No month gating: non-October dates simply return [].
 * - Delete is idempotent and tolerant (no 500 on missing or already-removed ids).
 * - If a change-log repo exists, remove logs first to avoid FK issues.
 */
@Service
public class SalesService {

    private final SaleRepository saleRepository;
    private final SalesChangeLogRepository changeLogRepository; // may be a no-op bean if not used

    public SalesService(SaleRepository saleRepository, SalesChangeLogRepository changeLogRepository) {
        this.saleRepository = saleRepository;
        this.changeLogRepository = changeLogRepository;
    }

    @Transactional(readOnly = true)
    public List<SaleRecord> listByDate(LocalDate date) {
        return saleRepository.findByDate(date);
    }

    @Transactional
    public SaleRecord create(String productName, int units, LocalDate date) {
        SaleRecord r = new SaleRecord();
        r.setProductName(productName.trim());
        r.setUnits(units);
        r.setDate(date);
        return saleRepository.save(r);
    }

    @Transactional
    public void delete(UUID id) {
        if (id == null) return;
        try {
            // If you log changes per sale, clear logs first (avoids FK violations)
            if (changeLogRepository != null) {
                changeLogRepository.deleteBySaleId(id);
            }
            // Idempotent delete: do nothing if it doesn't exist
            if (saleRepository.existsById(id)) {
                saleRepository.deleteById(id);
            }
        } catch (Exception ignored) {
            // Swallow to keep UX clean; your ApiExceptionHandler can be stricter if desired.
        }
    }
}
