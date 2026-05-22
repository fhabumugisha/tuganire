package com.tuganire.auth.controller;

import com.tuganire.auth.dto.ChangePasswordRequest;
import com.tuganire.auth.dto.UpdateProfileRequest;
import com.tuganire.auth.mapper.UserMapper;
import com.tuganire.auth.model.User;
import com.tuganire.auth.service.UserService;
import com.tuganire.payment.service.SubscriptionService;
import com.tuganire.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final SubscriptionService subscriptionService;

    @GetMapping
    public String showProfile(Model model) {
        User currentUser = userService.getCurrentUser();
        addCommonAttributes(model, currentUser);

        model.addAttribute("updateProfileRequest",
                new UpdateProfileRequest(currentUser.getFirstName(), currentUser.getLastName()));
        model.addAttribute("changePasswordRequest", new ChangePasswordRequest());

        return "auth/profile";
    }

    @PostMapping("/update")
    public String updateProfile(@Valid @ModelAttribute UpdateProfileRequest request, BindingResult result, Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser();

        if (result.hasErrors()) {
            addCommonAttributes(model, currentUser);
            model.addAttribute("activeTab", "profile");
            model.addAttribute("updateProfileRequest", request);
            model.addAttribute("changePasswordRequest", new ChangePasswordRequest());
            return "auth/profile";
        }

        userService.updateProfile(currentUser.getId(), request);
        redirectAttributes.addFlashAttribute("success", "profile.update.success");
        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute ChangePasswordRequest request, BindingResult result,
            Model model, RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser();

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "validation.password.mismatch");
        }

        if (result.hasErrors()) {
            model.addAttribute("changePasswordRequest", request);
            return "auth/profile/tab-security :: content";
        }

        try {
            userService.changePassword(currentUser.getId(), request.getCurrentPassword(), request.getNewPassword());
            redirectAttributes.addFlashAttribute("success", "profile.password.change.success");
        } catch (BusinessException e) {
            model.addAttribute("changePasswordRequest", request);
            model.addAttribute("passwordError", e.getMessage());
            return "auth/profile/tab-security :: content";
        }

        return "redirect:/profile";
    }

    @PostMapping("/delete-account")
    public String deleteAccount(@RequestParam String confirmPassword, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser();

        try {
            userService.deleteAccount(currentUser.getId(), confirmPassword);

            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            redirectAttributes.addFlashAttribute("success", "profile.delete.success");
            return "redirect:/login";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/profile";
        }
    }

    private void addCommonAttributes(Model model, User user) {
        model.addAttribute("user", userMapper.toResponse(user));
        subscriptionService.getUserSubscription(user.getId()).ifPresent(sub -> model.addAttribute("subscription", sub));
    }
}
