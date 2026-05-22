package com.tuganire.admin.controller;

import com.tuganire.admin.dto.UserAdminRow;
import com.tuganire.admin.dto.UserDetailDto;
import com.tuganire.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public String listUsers(@RequestParam(name = "q", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size, Model model) {
        Page<UserAdminRow> users = adminUserService.findUsers(search, page, size);
        model.addAttribute("users", users);
        model.addAttribute("search", search);
        model.addAttribute("activeNav", "users");
        return "admin/users/list";
    }

    @GetMapping("/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        UserDetailDto user = adminUserService.findById(id);
        model.addAttribute("user", user);
        model.addAttribute("activeNav", "users");
        return "admin/users/detail";
    }

    @PostMapping("/{id}/toggle-admin")
    public String toggleAdmin(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminUserService.toggleAdmin(id);
        redirectAttributes.addFlashAttribute("successMessage", "Admin status updated");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/anonymize")
    public String anonymize(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminUserService.anonymize(id);
        redirectAttributes.addFlashAttribute("successMessage", "User anonymized");
        return "redirect:/admin/users";
    }
}
