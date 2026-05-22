package com.tuganire.auth.controller;

import com.tuganire.auth.service.EmailVerificationService;
import com.tuganire.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final MessageSource messageSource;

    @GetMapping("/verify-email/{token}")
    public String verifyEmail(@PathVariable String token, RedirectAttributes redirectAttributes) {
        try {
            emailVerificationService.verify(token);

            String successMessage = messageSource.getMessage("auth.login.verified-success", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", successMessage);

            return "redirect:/login?verified=true";

        } catch (ResourceNotFoundException e) {
            log.warn("Email verification failed for token: {}", token);

            String errorMessage = messageSource.getMessage("auth.login.error.not-verified", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("error", errorMessage);

            return "redirect:/login?verifyError=true";
        }
    }
}
