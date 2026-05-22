package com.tuganire.admin.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tuganire.admin.dto.AdminDashboardModel;
import com.tuganire.admin.dto.DailyCostPoint;
import com.tuganire.admin.dto.ModelCostBreakdown;
import com.tuganire.admin.dto.UserCostRow;
import com.tuganire.admin.service.AdminStatsService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final AdminStatsService statsService;

    private final ObjectMapper objectMapper = buildMapper();

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        List<DailyCostPoint> dailyCost = statsService.dailyCostLast30Days();
        List<ModelCostBreakdown> costByModel = statsService.costByModelMtd();
        List<UserCostRow> topUsers = statsService.topUsersByCostMtd(10);
        BigDecimal llmCostMtd = statsService.totalLlmCostMtd();

        String dailyCostJson = toJson(dailyCost);
        String costByModelJson = toJson(costByModel);
        String topUsersJson = toJson(topUsers);

        AdminDashboardModel dashboardModel = AdminDashboardModel.builder().totalUsers(statsService.countAllUsers())
                .premiumUsers(statsService.countPremiumUsers()).freeUsers(statsService.countFreeUsers())
                .signupsToday(statsService.countSignupsToday()).llmCostMtd(llmCostMtd).dailyCostLast30Days(dailyCost)
                .costByModelMtd(costByModel).topUsersByCost(topUsers).dailyCostJson(dailyCostJson)
                .costByModelJson(costByModelJson).topUsersJson(topUsersJson).build();

        model.addAttribute("dashboard", dashboardModel);
        model.addAttribute("activeNav", "overview");
        return "admin/dashboard";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "[]";
        }
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
