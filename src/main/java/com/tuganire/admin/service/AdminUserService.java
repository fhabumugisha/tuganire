package com.tuganire.admin.service;

import com.tuganire.admin.dto.UserAdminRow;
import com.tuganire.admin.dto.UserDetailDto;
import com.tuganire.admin.repository.LlmUsageEventRepository;
import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.payment.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LlmUsageEventRepository llmUsageEventRepository;

    @Transactional(readOnly = true)
    public Page<UserAdminRow> findUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<User> all = userRepository.findAll();

        List<User> filtered = all.stream()
                .filter(u -> search == null || search.isBlank()
                        || u.getEmail().toLowerCase().contains(search.toLowerCase())
                        || (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(search.toLowerCase()))
                        || (u.getLastName() != null && u.getLastName().toLowerCase().contains(search.toLowerCase())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<UserAdminRow> rows = filtered.subList(start, end).stream().map(this::toAdminRow)
                .collect(Collectors.toList());

        return new PageImpl<>(rows, pageable, filtered.size());
    }

    private UserAdminRow toAdminRow(User user) {
        BigDecimal totalCost = llmUsageEventRepository.sumCostByUserId(user.getId());
        String fullName = buildFullName(user);
        return UserAdminRow.builder().id(user.getId()).email(user.getEmail()).fullName(fullName).plan(user.getPlan())
                .createdAt(user.getCreatedAt()).lastLoginAt(user.getLastLoginAt()).totalLlmCost(totalCost)
                .admin(user.isAdmin()).build();
    }

    @Transactional(readOnly = true)
    public UserDetailDto findById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        BigDecimal totalCost = llmUsageEventRepository.sumCostByUserId(id);
        var recentEvents = llmUsageEventRepository.findByUserIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20))
                .getContent();
        var subscription = subscriptionRepository.findByUserId(id).orElse(null);

        return UserDetailDto.builder().id(user.getId()).email(user.getEmail()).firstName(user.getFirstName())
                .lastName(user.getLastName()).plan(user.getPlan()).createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt()).admin(user.isAdmin()).subscription(subscription)
                .totalLlmCost(totalCost).recentLlmEvents(recentEvents).build();
    }

    public void toggleAdmin(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setAdmin(!user.isAdmin());
        userRepository.save(user);
        log.info("Toggled admin for user {}: admin={}", id, user.isAdmin());
    }

    public void anonymize(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setEmail("deleted-" + id + "@anonymized.local");
        user.setFirstName(null);
        user.setLastName(null);
        userRepository.save(user);
        log.info("Anonymized user {}", id);
    }

    private String buildFullName(User user) {
        if (user.getFirstName() == null && user.getLastName() == null) {
            return "";
        }
        return ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
    }
}
