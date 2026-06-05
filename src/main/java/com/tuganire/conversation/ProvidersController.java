package com.tuganire.conversation;

import com.tuganire.conversation.dto.ProvidersResponse;
import com.tuganire.conversation.dto.TtsProviderSwitchRequest;
import com.tuganire.llm.LlmProvider;
import com.tuganire.shared.exception.BusinessException;
import com.tuganire.stt.SttProvider;
import com.tuganire.tts.TtsProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller listing the registered TTS / STT / LLM providers and exposing the runtime switch for the active TTS
 * backend (ADR-004).
 *
 * <ul>
 * <li>{@code GET /api/v1/providers} — lists registered TTS, STT, and LLM providers.
 * <li>{@code PUT /api/v1/providers/tts} — switches the active TTS provider at runtime.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Providers", description = "Registered backends and runtime TTS switch")
public class ProvidersController {

    private final List<TtsProvider> ttsProviders;
    private final List<SttProvider> sttProviders;
    private final List<LlmProvider> llmProviders;
    private final TtsSettings ttsSettings;

    /**
     * Returns the names of all registered TTS, STT, and LLM providers.
     *
     * @return 200 with a map of provider type to name list
     */
    @GetMapping
    @Operation(summary = "List providers", description = "Get all registered provider names per type")
    public ResponseEntity<ProvidersResponse> getProviders() {
        List<String> tts = ttsProviders.stream().map(TtsProvider::name).toList();
        List<String> stt = sttProviders.stream().map(SttProvider::name).toList();
        List<String> llm = llmProviders.stream().map(LlmProvider::name).toList();
        return ResponseEntity.ok(new ProvidersResponse(tts, stt, llm, ttsSettings.getActiveProvider()));
    }

    /**
     * Switches the active TTS provider at runtime (ADR-004).
     *
     * <p>
     * The provider name must match one of the registered TTS provider names returned by {@code GET /api/v1/providers}.
     * An unknown name throws a {@link BusinessException} (→ 400).
     *
     * @param request
     *            the provider switch payload
     * @return 200 with the updated active provider name
     */
    @PutMapping("/tts")
    @Operation(summary = "Switch TTS provider", description = "Change the active TTS provider at runtime")
    public ResponseEntity<Map<String, String>> switchTtsProvider(@Valid @RequestBody TtsProviderSwitchRequest request) {
        boolean known = ttsProviders.stream().anyMatch(p -> p.name().equals(request.providerName()));
        if (!known) {
            throw new BusinessException("tts.provider.not-found");
        }
        ttsSettings.setActiveProvider(request.providerName());
        log.info("PUT /providers/tts: switched active TTS provider to '{}'", request.providerName());
        return ResponseEntity.ok(Map.of("activeTtsProvider", request.providerName()));
    }
}
