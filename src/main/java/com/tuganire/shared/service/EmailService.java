package com.tuganire.shared.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${tuganire.base-url}")
    private String baseUrl;

    @Value("${spring.application.name:Tuganire}")
    private String appName;

    public void sendPasswordResetEmail(String toEmail, String token, String userName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Réinitialisation de votre mot de passe - " + appName);

            String resetUrl = baseUrl + "/reset-password?token=" + token;

            Context context = new Context();
            context.setVariable("userName", userName);
            context.setVariable("resetUrl", resetUrl);

            String htmlContent = templateEngine.process("email/password-reset-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent successfully to {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendSubscriptionConfirmationEmail(String toEmail, String userName, String planName,
            String nextBillingDate) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Confirmation de votre abonnement Premium - " + appName);

            Context context = new Context();
            context.setVariable("userName", userName);
            context.setVariable("planName", planName);
            context.setVariable("nextBillingDate", nextBillingDate);
            context.setVariable("dashboardUrl", baseUrl + "/dashboard");
            context.setVariable("profileUrl", baseUrl + "/profile");

            String htmlContent = templateEngine.process("email/subscription-confirmation", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Subscription confirmation email sent successfully to {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send subscription confirmation email to {}", toEmail, e);
            // Don't throw - email failure shouldn't break the subscription flow
        }
    }
}
