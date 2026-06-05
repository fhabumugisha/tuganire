package com.tuganire.stt;

import com.tuganire.shared.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Registry that resolves {@link SttProvider} instances by name or by language code.
 *
 * <p>
 * All {@link SttProvider} beans discovered in the application context are registered automatically. The
 * {@link #forLanguage} method selects the first provider whose {@link SttProvider#supportsLanguage} returns
 * {@code true}; if none qualify (e.g. Kinyarwanda when only whisper is registered) it throws a
 * {@link BusinessException} with a clear message rather than returning a silently incorrect provider.
 */
@Service
@Slf4j
class SttProviderFactory {

    private final Map<String, SttProvider> byName;
    private final List<SttProvider> providers;

    SttProviderFactory(List<SttProvider> providers) {
        this.providers = providers;
        this.byName = providers.stream().collect(Collectors.toMap(SttProvider::name, Function.identity()));
        log.info("SttProviderFactory: registered providers = {}", byName.keySet());
    }

    /**
     * Returns the provider registered under the given name.
     *
     * @param name
     *            canonical provider name (e.g. {@code "whisper"})
     * @return the matching provider
     * @throws BusinessException
     *             if no provider with that name is registered
     */
    SttProvider get(String name) {
        SttProvider provider = byName.get(name);
        if (provider == null) {
            throw new BusinessException("stt.provider.not-found");
        }
        return provider;
    }

    /**
     * Returns the first registered provider that supports the given language code.
     *
     * <p>
     * If no provider supports {@code langCode} — most notably {@code "rw"} / Kinyarwanda, which whisper-1 does not
     * cover — a {@link BusinessException} is thrown so the pipeline surfaces the gap rather than hiding it.
     *
     * @param langCode
     *            BCP-47 language code (e.g. {@code "fr"}, {@code "en"}, {@code "rw"})
     * @return a provider supporting the language
     * @throws BusinessException
     *             with key {@code "stt.provider.no-rw-provider"} (for rw) or
     *             {@code "stt.provider.unsupported-language"} if no registered provider supports the language
     */
    SttProvider forLanguage(String langCode) {
        Optional<SttProvider> match = providers.stream().filter(p -> p.supportsLanguage(langCode)).findFirst();

        if (match.isEmpty()) {
            String key = "rw".equals(langCode) ? "stt.provider.no-rw-provider" : "stt.provider.unsupported-language";
            log.warn("SttProviderFactory: no STT provider registered for language '{}'. "
                    + "For Kinyarwanda, see STT-KINYARWANDA-NOTE.md for Sprint 2 alternatives.", langCode);
            throw new BusinessException(key);
        }

        return match.get();
    }
}
