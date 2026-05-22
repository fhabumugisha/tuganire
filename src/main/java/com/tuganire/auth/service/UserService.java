package com.tuganire.auth.service;

import com.tuganire.auth.dto.UpdateProfileRequest;
import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.PasswordResetTokenRepository;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.payment.repository.SubscriptionRepository;
import com.tuganire.shared.constant.PlanType;
import com.tuganire.shared.exception.BusinessException;
import com.tuganire.shared.exception.ResourceNotFoundException;
import com.tuganire.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal().equals("anonymousUser")) {
            throw new UnauthorizedException("User not authenticated");
        }

        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        return userRepository.findByEmail(email).orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional
    public User upgradeToPremium(Long userId) {
        User user = getUserById(userId);
        user.setPlan(PlanType.PREMIUM);
        return userRepository.save(user);
    }

    @Transactional
    public User downgradeTOFree(Long userId) {
        User user = getUserById(userId);
        user.setPlan(PlanType.FREE);
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        log.info("Profile updated for user {}", userId);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException("profile.password.current.incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user {}", userId);
    }

    @Transactional
    public void deleteAccount(Long userId, String confirmPassword) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(confirmPassword, user.getPassword())) {
            throw new BusinessException("profile.delete.password.incorrect");
        }

        String email = user.getEmail();

        // Delete subscription data
        subscriptionRepository.deleteByUserId(userId);

        // Delete password reset tokens
        passwordResetTokenRepository.deleteByUserId(userId);

        // Finally delete user (cascades to usage_stats via FK)
        userRepository.delete(user);
        log.info("Account deleted for user {}", email);
    }
}
