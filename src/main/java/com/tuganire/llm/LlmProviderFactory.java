package com.tuganire.llm;

import com.tuganire.config.LlmConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Collects all {@link LlmProvider} beans and exposes them by name for runtime selection.
 *
 * <p>
 * Mirrors {@code TtsProviderFactory} (ADR-004): providers are switchable at runtime, not at startup. The default and
 * fallback are resolved from {@link LlmConfig} (bound from {@code tuganire.llm.default-provider} and
 * {@code tuganire.llm.fallback-provider}).
 */
@Service
@Slf4j
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providers;
    private final LlmConfig llmConfig;

    public LlmProviderFactory(List<LlmProvider> allProviders, LlmConfig llmConfig) {
        this.providers = allProviders.stream().collect(Collectors.toMap(LlmProvider::name, Function.identity()));
        this.llmConfig = llmConfig;
        log.info("LlmProviderFactory initialized with providers: {}", this.providers.keySet());
    }

    /**
     * Returns the provider with the given name.
     *
     * @param name
     *            the provider identifier (e.g. {@code "openai"}, {@code "anthropic"})
     * @return the matching {@link LlmProvider}
     * @throws IllegalArgumentException
     *             if no provider with that name is registered
     */
    public LlmProvider get(String name) {
        LlmProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown LLM provider: '" + name + "'. Available: " + providers.keySet());
        }
        return provider;
    }

    /**
     * Returns the default provider as configured by {@code tuganire.llm.default-provider}.
     *
     * @return the default {@link LlmProvider}
     */
    public LlmProvider getDefault() {
        return get(llmConfig.defaultProvider());
    }

    /**
     * Returns the fallback provider as configured by {@code tuganire.llm.fallback-provider}.
     *
     * @return the fallback {@link LlmProvider}
     */
    public LlmProvider getFallback() {
        return get(llmConfig.fallbackProvider());
    }
}
