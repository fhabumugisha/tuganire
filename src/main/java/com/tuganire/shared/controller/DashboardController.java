package com.tuganire.shared.controller;

import com.tuganire.auth.model.User;
import com.tuganire.shared.constant.PlanType;
import com.tuganire.shared.service.FreemiumService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final FreemiumService freemiumService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal User currentUser) {
        model.addAttribute("user", currentUser);

        if (currentUser.getPlan() == PlanType.FREE) {
            int maxItems = freemiumService.getMaxItems();
            model.addAttribute("maxItems", maxItems);
        }

        return "dashboard";
    }
}
