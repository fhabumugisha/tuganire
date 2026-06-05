package com.tuganire.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuganire.cache.TranslationCache;
import com.tuganire.config.LlmConfig.ModelOption;
import com.tuganire.golden.GoldenDictionaryService;
import com.tuganire.llm.LlmProvider;
import com.tuganire.llm.LlmProviderFactory;
import com.tuganire.llm.LlmSettings;
import com.tuganire.postprocessor.KinyarwandaPostProcessor;
import com.tuganire.postprocessor.ProcessedTranslation;
import com.tuganire.translation.normalizer.FrenchNormalizer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TranslationServiceImpl}.
 *
 * <p>
 * Verifies the six-step pipeline ordering: cache hit short-circuits, golden-dictionary hit avoids the LLM, the normal
 * path calls the LLM and post-processor, and the fallback provider is used when the default fails transiently.
 */
@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");
    private static final String SOURCE = "Bonjour";
    private static final String TRANSLATED = "Muraho";
    private static final String PRIMARY_MODEL = "gpt-5.5";
    private static final String FALLBACK_MODEL = "claude-sonnet-4-6";

    @Mock
    private TranslationCache cache;

    @Mock
    private FrenchNormalizer normalizer;

    @Mock
    private GoldenDictionaryService goldenDict;

    @Mock
    private LlmProviderFactory llmFactory;

    @Mock
    private LlmProvider primaryProvider;

    @Mock
    private LlmProvider fallbackProvider;

    @Mock
    private KinyarwandaPostProcessor postProcessor;

    @Mock
    private LlmSettings llmSettings;

    private TranslationService service;

    @BeforeEach
    void setUp() {
        service = new TranslationServiceImpl(cache, normalizer, goldenDict, llmFactory, llmSettings, postProcessor,
                new SimpleMeterRegistry());
    }

    @Test
    void translate_returnsCachedResult_andDoesNotCallLlm() {
        // Given — cache already has this translation
        TranslationResponse cached = TranslationResponse.fromCache(SOURCE, TRANSLATED, null, 1.0, List.of());
        when(cache.find(SOURCE, FR, RW)).thenReturn(Optional.of(cached));

        // When
        TranslationResponse result = service.translate(SOURCE, FR, RW);

        // Then — LLM must not be invoked
        assertThat(result.fromCache()).isTrue();
        assertThat(result.translatedText()).isEqualTo(TRANSLATED);
        verify(llmFactory, never()).getDefault();
        verify(goldenDict, never()).lookup(anyString(), any(), any());
    }

    @Test
    void translate_returnsGoldenDictionaryHit_andDoesNotCallLlm() {
        // Given — cache miss, but golden dictionary has the entry
        when(cache.find(SOURCE, FR, RW)).thenReturn(Optional.empty());
        when(normalizer.normalize(SOURCE)).thenReturn(SOURCE);
        TranslationResponse goldenResponse = TranslationResponse.fromGoldenDictionary(SOURCE, TRANSLATED, List.of());
        when(goldenDict.lookup(SOURCE, FR, RW)).thenReturn(Optional.of(goldenResponse));

        // When
        TranslationResponse result = service.translate(SOURCE, FR, RW);

        // Then — LLM must not be invoked
        assertThat(result.fromGoldenDictionary()).isTrue();
        assertThat(result.translatedText()).isEqualTo(TRANSLATED);
        verify(llmFactory, never()).getDefault();
    }

    @Test
    void translate_callsLlmAndPostProcessor_whenCacheAndGoldenMiss() {
        // Given — full pipeline: cache miss, golden miss, LLM returns raw text
        when(cache.find(SOURCE, FR, RW)).thenReturn(Optional.empty());
        when(normalizer.normalize(SOURCE)).thenReturn(SOURCE);
        when(goldenDict.lookup(SOURCE, FR, RW)).thenReturn(Optional.empty());
        when(llmSettings.getActiveProvider()).thenReturn("openai");
        when(llmSettings.getModel()).thenReturn(PRIMARY_MODEL);
        when(llmFactory.get("openai")).thenReturn(primaryProvider);
        when(primaryProvider.name()).thenReturn("openai");
        when(primaryProvider.translate(SOURCE, FR, RW, PRIMARY_MODEL)).thenReturn("raw-rw");
        when(postProcessor.process("raw-rw", SOURCE, FR, RW))
                .thenReturn(new ProcessedTranslation(TRANSLATED, List.of("PLURAL_RESPECT")));

        // When
        TranslationResponse result = service.translate(SOURCE, FR, RW);

        // Then — LLM and post-processor both called; result stored in cache
        assertThat(result.fromCache()).isFalse();
        assertThat(result.fromGoldenDictionary()).isFalse();
        assertThat(result.translatedText()).isEqualTo(TRANSLATED);
        assertThat(result.appliedCorrections()).contains("PLURAL_RESPECT");
        verify(primaryProvider).translate(SOURCE, FR, RW, PRIMARY_MODEL);
        verify(postProcessor).process("raw-rw", SOURCE, FR, RW);
        verify(cache).put(eq(SOURCE), eq(FR), eq(RW), any());
    }

    @Test
    void translate_usesFallbackProvider_whenDefaultProviderThrowsTransient() {
        // Given — cache miss, golden miss, default LLM fails with a transient (IO) error
        when(cache.find(SOURCE, FR, RW)).thenReturn(Optional.empty());
        when(normalizer.normalize(SOURCE)).thenReturn(SOURCE);
        when(goldenDict.lookup(SOURCE, FR, RW)).thenReturn(Optional.empty());
        when(llmSettings.getActiveProvider()).thenReturn("openai");
        when(llmSettings.getModel()).thenReturn(PRIMARY_MODEL);
        when(llmSettings.getAvailableModels())
                .thenReturn(List.of(new ModelOption(PRIMARY_MODEL, "openai", "GPT-5.5 (OpenAI)"),
                        new ModelOption(FALLBACK_MODEL, "anthropic", "Claude Sonnet 4.6 (Anthropic)")));
        when(llmFactory.get("openai")).thenReturn(primaryProvider);
        when(llmFactory.getDefault()).thenReturn(primaryProvider);
        when(llmFactory.getFallback()).thenReturn(fallbackProvider);
        when(primaryProvider.name()).thenReturn("openai");
        when(fallbackProvider.name()).thenReturn("anthropic");
        when(primaryProvider.translate(SOURCE, FR, RW, PRIMARY_MODEL))
                .thenThrow(new RuntimeException("OpenAI timeout", new java.io.IOException("connect timed out")));
        when(fallbackProvider.translate(SOURCE, FR, RW, FALLBACK_MODEL)).thenReturn("fallback-rw");
        when(postProcessor.process("fallback-rw", SOURCE, FR, RW))
                .thenReturn(new ProcessedTranslation(TRANSLATED, List.of()));

        // When
        TranslationResponse result = service.translate(SOURCE, FR, RW);

        // Then — fallback provider used; result is still returned
        assertThat(result.translatedText()).isEqualTo(TRANSLATED);
        verify(fallbackProvider).translate(SOURCE, FR, RW, FALLBACK_MODEL);
    }

    @Test
    void translate_convenienceOverload_delegatesToMainMethod() {
        // Given — cache already has this translation
        TranslationResponse cached = TranslationResponse.fromCache(SOURCE, TRANSLATED, null, 1.0, List.of());
        when(cache.find(SOURCE, FR, RW)).thenReturn(Optional.of(cached));

        // When
        TranslationRequest request = new TranslationRequest(SOURCE, FR, RW, "session-1");
        TranslationResponse result = service.translate(request);

        // Then
        assertThat(result.fromCache()).isTrue();
        assertThat(result.translatedText()).isEqualTo(TRANSLATED);
    }
}
