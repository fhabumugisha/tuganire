package com.tuganire.admin.repository;

import com.tuganire.admin.model.LlmUsageEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmUsageEventRepository extends JpaRepository<LlmUsageEvent, Long> {

    Page<LlmUsageEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM LlmUsageEvent e WHERE e.createdAt BETWEEN :start AND :end")
    BigDecimal sumCostBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(e.totalTokens), 0) FROM LlmUsageEvent e WHERE e.createdAt BETWEEN :start AND :end")
    Long sumTokensBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e.model AS model, SUM(e.costUsd) AS totalCost, COUNT(e) AS callCount "
            + "FROM LlmUsageEvent e WHERE e.createdAt BETWEEN :start AND :end "
            + "GROUP BY e.model ORDER BY SUM(e.costUsd) DESC")
    List<Object[]> groupByModelBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e.user.id AS userId, e.user.email AS email, SUM(e.costUsd) AS totalCost "
            + "FROM LlmUsageEvent e WHERE e.user IS NOT NULL AND e.createdAt BETWEEN :start AND :end "
            + "GROUP BY e.user.id, e.user.email ORDER BY SUM(e.costUsd) DESC")
    List<Object[]> groupByUserBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query("SELECT CAST(e.createdAt AS LocalDate) AS day, SUM(e.costUsd) AS totalCost "
            + "FROM LlmUsageEvent e WHERE e.createdAt BETWEEN :start AND :end "
            + "GROUP BY CAST(e.createdAt AS LocalDate) ORDER BY CAST(e.createdAt AS LocalDate) ASC")
    List<Object[]> dailyCostBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(e.costUsd), 0) FROM LlmUsageEvent e WHERE e.user.id = :userId")
    BigDecimal sumCostByUserId(@Param("userId") Long userId);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    Page<LlmUsageEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
