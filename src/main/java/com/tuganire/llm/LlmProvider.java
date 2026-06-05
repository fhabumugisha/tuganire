package com.tuganire.llm;

import com.tuganire.translation.StructuredTranslation;
import java.util.Locale;

/**
 * Abstraction over any LLM backend that can translate text.
 *
 * <p>
 * Implementations are swappable at runtime (ADR-004). No {@code org.springframework.ai.*} types appear here so that the
 * Spring AI M4 → GA migration only touches implementations.
 *
 * <p>
 * Providers are <b>stateless</b> with regard to model selection: the orchestrator ({@code TranslationServiceImpl})
 * decides which model to call and passes the id explicitly. Temperature, on the other hand, is read by the provider on
 * every call because its semantics are provider-specific (e.g. GPT-5.x rejects custom values).
 */
public interface LlmProvider {

    /**
     * Translates plain text from the source locale to the target locale using the given model id.
     *
     * @param text
     *            the text to translate
     * @param src
     *            the source language locale
     * @param tgt
     *            the target language locale
     * @param modelId
     *            the model id to call (must be valid for this provider)
     * @return translated text
     */
    String translate(String text, Locale src, Locale tgt, String modelId);

    /**
     * Returns the canonical name of this provider (e.g. {@code "openai"}, {@code "anthropic"}).
     *
     * @return provider name
     */
    String name();

    /**
     * Translates text and returns structured metadata. Every implementation must support structured output (no default
     * implementation): if the underlying model cannot produce one, the provider must convert its plain output into a
     * best-effort {@link StructuredTranslation}.
     *
     * @param text
     *            the text to translate
     * @param src
     *            the source language locale
     * @param tgt
     *            the target language locale
     * @param modelId
     *            the model id to call (must be valid for this provider)
     * @return structured translation (never {@code null})
     */
    StructuredTranslation translateStructured(String text, Locale src, Locale tgt, String modelId);
}
