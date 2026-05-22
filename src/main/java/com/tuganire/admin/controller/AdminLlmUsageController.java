package com.tuganire.admin.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tuganire.admin.model.LlmUsageEvent;
import com.tuganire.admin.repository.LlmUsageEventRepository;
import com.tuganire.admin.service.AdminStatsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/llm-usage")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminLlmUsageController {

    private final AdminStatsService statsService;
    private final LlmUsageEventRepository llmUsageEventRepository;

    private final ObjectMapper objectMapper = buildMapper();

    @GetMapping
    public String llmUsage(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size, Model model) {

        LocalDateTime mtdStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal costMtd = statsService.totalLlmCostMtd();
        Long tokensMtd = llmUsageEventRepository.sumTokensBetween(mtdStart, now);
        long callsMtd = llmUsageEventRepository.countByCreatedAtBetween(mtdStart, now);

        Page<LlmUsageEvent> events = llmUsageEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        String dailyCostJson = toJson(statsService.dailyCostLast30Days());
        String costByModelJson = toJson(statsService.costByModelMtd());
        String topUsersJson = toJson(statsService.topUsersByCostMtd(10));

        model.addAttribute("costMtd", costMtd);
        model.addAttribute("tokensMtd", tokensMtd);
        model.addAttribute("callsMtd", callsMtd);
        model.addAttribute("events", events);
        model.addAttribute("dailyCostJson", dailyCostJson);
        model.addAttribute("costByModelJson", costByModelJson);
        model.addAttribute("topUsersJson", topUsersJson);
        model.addAttribute("activeNav", "llm");
        return "admin/llm-usage";
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
