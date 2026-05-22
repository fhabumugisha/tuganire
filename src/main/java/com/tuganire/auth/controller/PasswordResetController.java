package com.tuganire.auth.controller;

import com.tuganire.auth.dto.ForgotPasswordRequest;
import com.tuganire.auth.dto.ResetPasswordRequest;
import com.tuganire.auth.exception.ExpiredTokenException;
import com.tuganire.auth.exception.InvalidTokenException;
import com.tuganire.auth.service.PasswordResetService;
import com.tuganire.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final MessageSource messageSource;

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordRequest", new ForgotPasswordRequest());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@Valid @ModelAttribute ForgotPasswordRequest request, BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "auth/forgot-password";
        }

        try {
            passwordResetService.createResetToken(request.getEmail());

            String successMessage = messageSource.getMessage("auth.forgot.password.success", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", successMessage);

        } catch (ResourceNotFoundException e) {
            String errorMessage = messageSource.getMessage("auth.forgot.password.email.not.found", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("error", errorMessage);
        }

        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        if (!passwordResetService.validateToken(token)) {
            String errorMessage = messageSource.getMessage("auth.reset.password.token.invalid", null,
                    LocaleContextHolder.getLocale());
            model.addAttribute("error", errorMessage);
            return "error/business-error";
        }

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken(token);
        model.addAttribute("resetPasswordRequest", resetRequest);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@Valid @ModelAttribute ResetPasswordRequest request, BindingResult result,
            RedirectAttributes redirectAttributes, Model model) {

        if (result.hasErrors()) {
            return "auth/reset-password";
        }

        try {
            passwordResetService.resetPassword(request.getToken(), request.getPassword());

            String successMessage = messageSource.getMessage("auth.reset.password.success", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", successMessage);

            return "redirect:/login";

        } catch (InvalidTokenException | ExpiredTokenException e) {
            String errorMessage = messageSource.getMessage("auth.reset.password.token.invalid", null,
                    LocaleContextHolder.getLocale());
            model.addAttribute("error", errorMessage);
            model.addAttribute("resetPasswordRequest", request);
            return "auth/reset-password";
        }
    }
}
