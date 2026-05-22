package com.tuganire.auth.controller;

import com.tuganire.auth.dto.RegisterRequest;
import com.tuganire.auth.dto.UserResponse;
import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.auth.service.AuthService;
import com.tuganire.auth.service.AuthenticationSessionService;
import com.tuganire.auth.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Controller handling authentication-related endpoints. Manages user registration and login form display.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthenticationSessionService sessionService;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final MessageSource messageSource;

    @Value("${tuganire.base-url}")
    private String baseUrl;

    /** Exposed to all templates of this controller: true when GOOGLE_CLIENT_ID is configured. */
    @Value("${tuganire.oauth.google.client-id:}")
    private String googleClientId;

    @org.springframework.web.bind.annotation.ModelAttribute("googleOAuthEnabled")
    public boolean googleOAuthEnabled() {
        return googleClientId != null && !googleClientId.isBlank();
    }

    /**
     * Displays the registration form.
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    /**
     * Processes user registration. On success, automatically logs in the user and redirects to dashboard.
     *
     * @param registerRequest
     *            the registration form data
     * @param result
     *            the binding result for validation errors
     * @param redirectAttributes
     *            attributes for redirect
     * @return the view name or redirect URL
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest, BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "auth/register";
        }

        // Register the user
        UserResponse userResponse = authService.register(registerRequest);

        User user = userRepository.findById(userResponse.getId())
                .orElseThrow(() -> new IllegalStateException("User not found after registration"));

        // Send email verification instead of auto-login
        String token = emailVerificationService.createTokenForUser(user);
        String verifyUrl = baseUrl + "/verify-email/" + token;
        emailVerificationService.sendVerificationEmail(user.getEmail(), verifyUrl);

        // Add check-inbox message
        String successMessage = messageSource.getMessage("auth.register.success.check-inbox", null,
                LocaleContextHolder.getLocale());
        redirectAttributes.addFlashAttribute("success", successMessage);

        log.info("User {} registered, verification email sent", user.getEmail());
        return "redirect:/login?registered=true";
    }

    /**
     * Displays the login form with optional error or logout messages.
     */
    @GetMapping("/login")
    public String showLoginForm(@RequestParam(required = false) String error,
            @RequestParam(required = false) String logout, @RequestParam(required = false) String verified,
            @RequestParam(required = false) String registered, @RequestParam(required = false) String verifyError,
            Model model) {

        if (error != null) {
            String key = "oauth".equals(error) ? "auth.login.error.oauth" : "auth.login.error";
            String errorMessage = messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
            model.addAttribute("error", errorMessage);
        }

        if (logout != null) {
            String logoutMessage = messageSource.getMessage("auth.logout.success", null,
                    LocaleContextHolder.getLocale());
            model.addAttribute("success", logoutMessage);
        }

        if (verified != null) {
            String verifiedMessage = messageSource.getMessage("auth.login.verified-success", null,
                    LocaleContextHolder.getLocale());
            model.addAttribute("success", verifiedMessage);
        }

        if (registered != null) {
            String registeredMessage = messageSource.getMessage("auth.register.success.check-inbox", null,
                    LocaleContextHolder.getLocale());
            model.addAttribute("info", registeredMessage);
        }

        if (verifyError != null) {
            String verifyErrorMessage = messageSource.getMessage("auth.login.error.not-verified", null,
                    LocaleContextHolder.getLocale());
            model.addAttribute("error", verifyErrorMessage);
        }

        return "auth/login";
    }
}
