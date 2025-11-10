package com.julian.publixai.repository;

import com.julian.publixai.model.SaleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<SaleRecord, UUID> {

    List<SaleRecord> findByDate(LocalDate date);

    // ---- interface projections used by SalesStatsService ----
    interface SumRow {
        String getProductName();
        Long getUnits();
    }

    interface AvgRow {
        String getProductName();
        Double getAvgUnits();
    }

    // Sum units per product over a date range (desc by units)
    @Query("""
           select s.productName as productName, sum(s.units) as units
             from SaleRecord s
            where s.date between :start and :end
            group by s.productName
            order by sum(s.units) desc
           """)
    List<SumRow> sumByProductBetween(@Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    // Average units per day per product over a date range (PostgreSQL native)
    // NOTE: If your table name differs, change "sales" below.
    @Query(value = """
            select product_name as productName,
                   (sum(units)::double precision /
                       greatest(date_part('day', :end - :start) + 1, 1)) as avgUnits
              from sales
             where date between :start and :end
             group by product_name
             order by 2 desc
            """, nativeQuery = true)
    List<AvgRow> avgPerDayByProductBetween(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    // (Optional) single-day sum
    @Query("""
           select s.productName as productName, sum(s.units) as units
             from SaleRecord s
            where s.date = :date
            group by s.productName
            order by sum(s.units) desc
           """)
    List<SumRow> sumByProductForDate(@Param("date") LocalDate date);
}
