package com.tuganire.admin.service;

import com.tuganire.admin.dto.DailyCostPoint;
import com.tuganire.admin.dto.ModelCostBreakdown;
import com.tuganire.admin.dto.UserCostRow;
import com.tuganire.admin.repository.LlmUsageEventRepository;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.shared.constant.PlanType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final UserRepository userRepository;
    private final LlmUsageEventRepository llmUsageEventRepository;

    public long countAllUsers() {
        return userRepository.count();
    }

    public long countPremiumUsers() {
        return userRepository.findAll().stream().filter(u -> u.getPlan() == PlanType.PREMIUM).count();
    }

    public long countFreeUsers() {
        return userRepository.findAll().stream().filter(u -> u.getPlan() == PlanType.FREE).count();
    }

    public long countSignupsToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return userRepository.findAll().stream().filter(u -> u.getCreatedAt() != null
                && !u.getCreatedAt().isBefore(startOfDay) && !u.getCreatedAt().isAfter(endOfDay)).count();
    }

    public BigDecimal totalLlmCostMtd() {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return llmUsageEventRepository.sumCostBetween(start, end);
    }

    public List<DailyCostPoint> dailyCostLast30Days() {
        LocalDateTime start = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        List<Object[]> rows = llmUsageEventRepository.dailyCostBetween(start, end);
        List<DailyCostPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            BigDecimal cost = row[1] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            result.add(new DailyCostPoint(date, cost));
        }
        return result;
    }

    public List<ModelCostBreakdown> costByModelMtd() {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        List<Object[]> rows = llmUsageEventRepository.groupByModelBetween(start, end);
        List<ModelCostBreakdown> result = new ArrayList<>();
        for (Object[] row : rows) {
            String model = (String) row[0];
            BigDecimal cost = row[1] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            long count = ((Number) row[2]).longValue();
            result.add(new ModelCostBreakdown(model, cost, count));
        }
        return result;
    }

    public List<UserCostRow> topUsersByCostMtd(int limit) {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        List<Object[]> rows = llmUsageEventRepository.groupByUserBetween(start, end, PageRequest.of(0, limit));
        List<UserCostRow> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long userId = ((Number) row[0]).longValue();
            String email = (String) row[1];
            BigDecimal cost = row[2] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            result.add(new UserCostRow(userId, email, cost));
        }
        return result;
    }
}
