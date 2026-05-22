package com.tuganire.auth.service;

import com.tuganire.auth.model.EmailVerificationToken;
import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.EmailVerificationTokenRepository;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.shared.exception.ResourceNotFoundException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final int EXPIRATION_HOURS = 24;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${spring.application.name:Tuganire}")
    private String appName;

    @Transactional
    public String createTokenForUser(User user) {
        tokenRepository.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPIRATION_HOURS);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder().token(rawToken).user(user)
                .expiresAt(expiresAt).build();

        tokenRepository.save(verificationToken);

        log.info("Email verification token created for user: {}", user.getEmail());
        return rawToken;
    }

    @Transactional
    public User verify(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Email verification token not found or already used"));

        if (verificationToken.isExpired()) {
            throw new ResourceNotFoundException("Email verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        tokenRepository.delete(verificationToken);

        log.info("Email verified successfully for user: {}", user.getEmail());
        return user;
    }

    public void sendVerificationEmail(String toEmail, String verifyUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Vérifiez votre email - " + appName);

            Context context = new Context();
            context.setVariable("verifyUrl", verifyUrl);

            String htmlContent = templateEngine.process("email/email-verification", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent successfully to {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}", toEmail, e);
            // Don't throw — email failure should not block registration
        }
    }
}
