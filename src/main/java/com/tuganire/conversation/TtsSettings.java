package com.tuganire.conversation;

import com.tuganire.config.TtsConfig;
import com.tuganire.tts.TtsProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Holds the currently active TTS provider name (ADR-004: switchable at runtime, no restart).
 *
 * <p>
 * Mirrors {@link com.tuganire.llm.LlmSettings} for the LLM stack: a process-global tuning knob, not a per-user
 * preference. Lifted out of the former monolithic {@code SessionController} so each REST controller has a single
 * responsibility (SRP).
 */
@Component
public class TtsSettings {

    private static final String NONE = "none";

    private final AtomicReference<String> activeProvider;

    public TtsSettings(List<TtsProvider> ttsProviders, TtsConfig ttsConfig) {
        this.activeProvider = new AtomicReference<>(initialProvider(ttsProviders, ttsConfig));
    }

    /**
     * Resolves the initial active provider: the configured {@code default-provider} when it is actually registered,
     * otherwise the first registered provider, or {@code "none"} when none are registered.
     */
    private static String initialProvider(List<TtsProvider> ttsProviders, TtsConfig ttsConfig) {
        if (ttsProviders.isEmpty()) {
            return NONE;
        }
        String configured = ttsConfig.defaultProvider();
        boolean registered = ttsProviders.stream().anyMatch(p -> p.name().equals(configured));
        return registered ? configured : ttsProviders.get(0).name();
    }

    /** @return the name of the currently active TTS provider, or {@code "none"} if no provider is registered */
    public String getActiveProvider() {
        return activeProvider.get();
    }

    /**
     * Atomically replaces the active provider. The caller must validate {@code providerName} against the registered TTS
     * providers; this method does not.
     *
     * @param providerName
     *            new active provider name
     */
    public void setActiveProvider(String providerName) {
        this.activeProvider.set(providerName);
    }
}
