package com.julian.publixai.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.julian.publixai.model.Discount;

public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    List<Discount> findByProductName(String productName);

    @Query("SELECT d FROM Discount d WHERE d.productName = :productName AND :date BETWEEN d.startDate AND d.endDate")
    List<Discount> findActiveDiscountsForProduct(@Param("productName") String productName,
            @Param("date") LocalDate date);

    @Query("SELECT d FROM Discount d WHERE :date BETWEEN d.startDate AND d.endDate")
    List<Discount> findActiveDiscounts(@Param("date") LocalDate date);

    List<Discount> findByProductNameAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String productName, LocalDate endDate, LocalDate startDate);

    /**
     * Find discounts that overlap with a date range for a specific product.
     * Optimized query to avoid loading all discounts into memory.
     */
    @Query("SELECT d FROM Discount d WHERE d.productName = :productName AND " +
            "d.startDate <= :endDate AND d.endDate >= :startDate")
    List<Discount> findDiscountsOverlappingRange(
            @Param("productName") String productName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Find discounts that overlap with a date range (fuzzy product name matching).
     * Uses LIKE for partial matching.
     */
    @Query("SELECT d FROM Discount d WHERE LOWER(d.productName) LIKE LOWER(CONCAT('%', :productName, '%')) AND " +
            "d.startDate <= :endDate AND d.endDate >= :startDate")
    List<Discount> findDiscountsOverlappingRangeFuzzy(
            @Param("productName") String productName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
