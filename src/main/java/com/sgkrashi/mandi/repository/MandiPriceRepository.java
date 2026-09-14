package com.sgkrashi.mandi.repository;

import com.sgkrashi.mandi.entity.MandiPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MandiPriceRepository extends JpaRepository<MandiPrice, Long> {

    Optional<MandiPrice> findByCommodityAndMarketNameAndPriceDate(String commodity, String marketName, LocalDate priceDate);

    @Query("""
            SELECT p FROM MandiPrice p
            WHERE (:commodity IS NULL OR p.commodity = :commodity)
              AND (:state IS NULL OR p.state = :state)
              AND (:market IS NULL OR p.marketName = :market)
            ORDER BY p.priceDate DESC, p.commodity ASC
            """)
    Page<MandiPrice> search(@Param("commodity") String commodity, @Param("state") String state, @Param("market") String market, Pageable pageable);

    @Query("SELECT DISTINCT p.commodity FROM MandiPrice p ORDER BY p.commodity ASC")
    List<String> findDistinctCommodities();

    @Query("SELECT DISTINCT p.state FROM MandiPrice p ORDER BY p.state ASC")
    List<String> findDistinctStates();

    @Query("SELECT DISTINCT p.marketName FROM MandiPrice p WHERE (:state IS NULL OR p.state = :state) ORDER BY p.marketName ASC")
    List<String> findDistinctMarkets(@Param("state") String state);

    @Query("""
            SELECT p FROM MandiPrice p
            WHERE p.commodity = :commodity
              AND (:state IS NULL OR p.state = :state)
              AND (:market IS NULL OR p.marketName = :market)
            ORDER BY p.priceDate ASC
            """)
    List<MandiPrice> findTrend(@Param("commodity") String commodity, @Param("state") String state, @Param("market") String market);

    @Query("SELECT MAX(p.updatedAt) FROM MandiPrice p")
    Optional<java.time.Instant> findLastSyncedAt();

    long count();
}
