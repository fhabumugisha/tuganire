package com.tuganire.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuganire.feedback.FeedbackRepo;
import com.tuganire.feedback.FeedbackType;
import com.tuganire.llm.LlmProvider;
import com.tuganire.llm.LlmProviderFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end integration test covering the full translation and feedback pipeline.
 *
 * <p>
 * Verifies that {@code POST /api/v1/translate} returns a corrected Kinyarwanda translation and that
 * {@code POST /api/v1/feedback} persists the feedback entry in the database. The LLM is stubbed via {@link MockitoBean}
 * so no real network call is made.
 */
class EndToEndIT extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeedbackRepo feedbackRepo;

    @MockitoBean
    private LlmProviderFactory llmProviderFactory;

    // Two LlmProvider beans exist (openAiLlmProvider, claudeLlmProvider); target one by name so the
    // override is unambiguous. The factory is mocked to hand this stub back, so which one is replaced
    // is irrelevant to the test.
    @MockitoBean(name = "openAiLlmProvider")
    private LlmProvider stubbedLlmProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        when(stubbedLlmProvider.name()).thenReturn("openai");
        // Return a raw translation containing a known singular form that PluralRespectRule should correct
        when(stubbedLlmProvider.translate(anyString(), any(), any(), anyString())).thenReturn("Ndakwinginze gufasha");
        when(llmProviderFactory.get(anyString())).thenReturn(stubbedLlmProvider);
        when(llmProviderFactory.getDefault()).thenReturn(stubbedLlmProvider);
        when(llmProviderFactory.getFallback()).thenReturn(stubbedLlmProvider);
    }

    @Test
    void postTranslate_returnsCorrectedKinyarwandaTranslation() throws Exception {
        // Given — French input that will be sent to the stubbed LLM
        Map<String, String> body = Map.of("sourceText", "S'il vous plaît, aidez-moi", "sourceLanguage", "fr",
                "targetLanguage", "rw", "sessionId", "it-session-1");

        // When / Then — 200 OK with corrected Kinyarwanda (PluralRespectRule: ndakwinginze → ndabinginze)
        mockMvc.perform(post("/api/v1/translate").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isOk())
                .andExpect(jsonPath("$.translatedText").value("ndabinginze gufasha"))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.fromGoldenDictionary").value(false))
                .andExpect(jsonPath("$.appliedCorrections[0]").value("PLURAL_RESPECT"));
    }

    @Test
    void postFeedback_persistsFeedbackEntry() throws Exception {
        // Given — a feedback submission for a previous translation
        long countBefore = feedbackRepo.countByType(FeedbackType.THUMBS_UP);
        Map<String, Object> body = Map.of("sessionId", "it-session-2", "translationId", "tr-42", "type", "THUMBS_UP");

        // When / Then — 202 Accepted
        mockMvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isAccepted());

        // And the feedback is persisted
        assertThat(feedbackRepo.countByType(FeedbackType.THUMBS_UP)).isEqualTo(countBefore + 1);
    }

    @Test
    void postTranslate_returns400_whenSourceTextIsBlank() throws Exception {
        // Given — invalid request with blank source text
        Map<String, String> body = Map.of("sourceText", " ", "sourceLanguage", "fr", "targetLanguage", "rw",
                "sessionId", "it-session-3");

        // When / Then — Bean Validation should reject with 400
        mockMvc.perform(post("/api/v1/translate").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest());
    }
}
