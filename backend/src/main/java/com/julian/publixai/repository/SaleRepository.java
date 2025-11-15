package com.julian.publixai.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.julian.publixai.model.SaleRecord;

public interface SaleRepository extends JpaRepository<SaleRecord, UUID> {

        List<SaleRecord> findByDate(LocalDate date);

        List<SaleRecord> findByDateBetween(LocalDate start, LocalDate end);

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

        // Get all distinct product names
        @Query("SELECT DISTINCT s.productName FROM SaleRecord s ORDER BY s.productName")
        List<String> findDistinctProductNames();

        // Find sales by product name and date range (optimized query)
        @Query("SELECT s FROM SaleRecord s WHERE s.productName = :productName AND s.date BETWEEN :startDate AND :endDate")
        List<SaleRecord> findByProductNameAndDateBetween(
                        @Param("productName") String productName,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
