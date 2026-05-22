package com.tuganire.auth.service;

import com.tuganire.auth.exception.ExpiredTokenException;
import com.tuganire.auth.exception.InvalidTokenException;
import com.tuganire.auth.model.PasswordResetToken;
import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.PasswordResetTokenRepository;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.shared.exception.ResourceNotFoundException;
import com.tuganire.shared.service.EmailService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int EXPIRATION_MINUTES = 30;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void createResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        tokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        PasswordResetToken resetToken = PasswordResetToken.builder().token(token).user(user).expiryDate(expiryDate)
                .build();

        tokenRepository.save(resetToken);

        String userName = user.getFirstName() != null ? user.getFirstName() : user.getEmail();
        emailService.sendPasswordResetEmail(user.getEmail(), token, userName);

        log.info("Password reset token created for user: {}", email);
    }

    public boolean validateToken(String token) {
        return tokenRepository.findByToken(token).map(resetToken -> !resetToken.isExpired()).orElse(false);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid reset token"));

        if (resetToken.isExpired()) {
            throw new ExpiredTokenException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(resetToken);

        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.deleteByExpiryDateBefore(now);
        log.info("Expired password reset tokens cleaned up");
    }
}
