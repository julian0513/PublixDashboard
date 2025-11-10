package com.julian.publixai.repository;

import com.julian.publixai.model.SalesChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SalesChangeLogRepository extends JpaRepository<SalesChangeLog, UUID> {
    void deleteBySaleId(UUID saleId);
}
