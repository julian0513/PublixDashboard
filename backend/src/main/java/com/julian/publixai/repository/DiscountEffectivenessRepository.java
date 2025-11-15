package com.julian.publixai.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.julian.publixai.model.DiscountEffectiveness;

public interface DiscountEffectivenessRepository extends JpaRepository<DiscountEffectiveness, UUID> {

        List<DiscountEffectiveness> findByProductName(String productName);

        List<DiscountEffectiveness> findByProductNameOrderByUnitsSoldDesc(String productName);

        @Query("SELECT de FROM DiscountEffectiveness de WHERE de.productName = :productName AND de.date BETWEEN :startDate AND :endDate")
        List<DiscountEffectiveness> findByProductNameAndDateBetween(
                        @Param("productName") String productName,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * Find discount effectiveness records with fuzzy product name matching.
         * Uses LIKE for partial matching to handle product name variations.
         */
        @Query("SELECT de FROM DiscountEffectiveness de WHERE LOWER(de.productName) LIKE LOWER(CONCAT('%', :productName, '%')) AND "
                        +
                        "de.date BETWEEN :startDate AND :endDate")
        List<DiscountEffectiveness> findByProductNameFuzzyAndDateBetween(
                        @Param("productName") String productName,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
