package com.tuganire.auth.service;

import com.tuganire.auth.dto.RegisterRequest;
import com.tuganire.auth.dto.UserResponse;
import com.tuganire.auth.exception.EmailAlreadyExistsException;
import com.tuganire.auth.mapper.UserMapper;
import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.shared.constant.PlanType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for authentication operations. Handles user registration and login tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Registers a new user with the provided information.
     *
     * @param request
     *            the registration request containing user details
     * @return the registered user as a UserResponse
     * @throws EmailAlreadyExistsException
     *             if email is already registered
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());

        validateEmailNotExists(request.getEmail());

        User user = buildNewUser(request);
        User savedUser = userRepository.save(user);

        log.info("User registered successfully with id: {}", savedUser.getId());
        return userMapper.toResponse(savedUser);
    }

    /**
     * Updates the last login timestamp for a user.
     *
     * @param email
     *            the user's email
     */
    @Transactional
    public void updateLastLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            log.debug("Updated last login for user: {}", email);
        });
    }

    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration attempt with existing email: {}", email);
            throw new EmailAlreadyExistsException(email);
        }
    }

    private User buildNewUser(RegisterRequest request) {
        return User.builder().email(request.getEmail()).password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName()).lastName(request.getLastName()).plan(PlanType.FREE).build();
    }
}
