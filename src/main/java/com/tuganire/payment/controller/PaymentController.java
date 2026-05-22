package com.tuganire.payment.controller;

import com.tuganire.auth.model.User;
import com.tuganire.payment.dto.CheckoutRequest;
import com.tuganire.payment.dto.SubscriptionResponse;
import com.tuganire.payment.service.StripeService;
import com.tuganire.payment.service.SubscriptionService;
import com.tuganire.shared.constant.PlanType;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final MessageSource messageSource;

    @GetMapping("/pricing")
    public String showPricing(@AuthenticationPrincipal User user, Model model) {
        if (user != null) {
            model.addAttribute("userPlan", user.getPlan());
            Optional<SubscriptionResponse> subscription = subscriptionService.getUserSubscription(user.getId());
            subscription.ifPresent(sub -> model.addAttribute("subscription", sub));
        }

        model.addAttribute("checkoutRequest", new CheckoutRequest());
        return "payment/pricing";
    }

    @PostMapping("/checkout")
    public String createCheckout(@AuthenticationPrincipal User user, @Valid @ModelAttribute CheckoutRequest request,
            BindingResult result, RedirectAttributes redirectAttributes, Model model) {

        if (result.hasErrors()) {
            model.addAttribute("userPlan", user.getPlan());
            return "payment/pricing";
        }

        if (user.getPlan() == PlanType.PREMIUM) {
            String errorMessage = messageSource.getMessage("payment.error.already.subscribed", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("error", errorMessage);
            return "redirect:/payment/pricing";
        }

        try {
            String checkoutUrl = subscriptionService.createSubscription(user, request.getPlan());
            return "redirect:" + checkoutUrl;
        } catch (Exception e) {
            log.error("Checkout failed for user {}", user.getId(), e);
            String errorMessage = messageSource.getMessage("payment.error.stripe", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("error", errorMessage);
            return "redirect:/payment/pricing";
        }
    }

    @GetMapping("/success")
    public String paymentSuccess(@RequestParam(name = "session_id", required = false) String sessionId, Model model) {
        model.addAttribute("sessionId", sessionId);
        return "payment/success";
    }

    @GetMapping("/cancel")
    public String paymentCancel() {
        return "payment/cancel";
    }

    @GetMapping("/manage")
    public String manageSubscription(@AuthenticationPrincipal User user) {
        if (user.getPlan() != PlanType.PREMIUM || user.getStripeCustomerId() == null) {
            return "redirect:/profile";
        }

        String portalUrl = stripeService.getPortalSession(user.getStripeCustomerId());
        return "redirect:" + portalUrl;
    }

    @PostMapping("/cancel-subscription")
    public String cancelSubscription(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "false") boolean immediately, RedirectAttributes redirectAttributes) {

        if (user.getPlan() != PlanType.PREMIUM) {
            return "redirect:/profile";
        }

        try {
            subscriptionService.cancelUserSubscription(user.getId(), immediately);

            String successMessage = messageSource.getMessage("payment.canceled.success", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", successMessage);
        } catch (Exception e) {
            log.error("Failed to cancel subscription for user {}", user.getId(), e);
            String errorMessage = messageSource.getMessage("payment.error.stripe", null,
                    LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("error", errorMessage);
        }

        return "redirect:/profile";
    }
}
