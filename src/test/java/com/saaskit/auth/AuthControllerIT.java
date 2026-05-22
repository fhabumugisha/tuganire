package com.tuganire.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuganire.auth.model.User;
import com.tuganire.auth.repository.EmailVerificationTokenRepository;
import com.tuganire.auth.repository.UserRepository;
import com.tuganire.support.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class MailMockConfig {

        @Bean
        @Primary
        JavaMailSender javaMailSender() {
            JavaMailSender sender = Mockito.mock(JavaMailSender.class);
            when(sender.createMimeMessage()).thenReturn(Mockito.mock(MimeMessage.class));
            Mockito.doNothing().when(sender).send(any(MimeMessage.class));
            return sender;
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    @BeforeEach
    void cleanState() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesUnverifiedUserAndIssuesToken() throws Exception {
        mockMvc.perform(post("/register").with(csrf()).param("email", "alice@example.com")
                .param("password", "password123").param("firstName", "Alice").param("lastName", "Anderson"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered=true"));

        User saved = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(tokenRepository.findAll()).hasSize(1).first()
                .satisfies(token -> assertThat(token.getUser().getId()).isEqualTo(saved.getId()));
    }

    @Test
    void verifyEmailMarksUserVerifiedAndRemovesToken() throws Exception {
        mockMvc.perform(post("/register").with(csrf()).param("email", "bob@example.com")
                .param("password", "password123").param("firstName", "Bob").param("lastName", "Brown"))
                .andExpect(status().is3xxRedirection());

        String token = tokenRepository.findAll().getFirst().getToken();

        mockMvc.perform(get("/verify-email/{token}", token)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verified=true"));

        assertThat(userRepository.findByEmail("bob@example.com").orElseThrow().isEmailVerified()).isTrue();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void verifyEmailWithUnknownTokenRedirectsToError() throws Exception {
        mockMvc.perform(get("/verify-email/{token}", "definitely-not-a-real-token"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?verifyError=true"));
    }

    @Test
    void loginWithWrongPasswordRedirectsToErrorPage() throws Exception {
        mockMvc.perform(post("/register").with(csrf()).param("email", "carol@example.com")
                .param("password", "password123").param("firstName", "Carol").param("lastName", "Carter"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(formLogin("/login").user("carol@example.com").password("wrong-password"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/login?error*"));
    }
}
