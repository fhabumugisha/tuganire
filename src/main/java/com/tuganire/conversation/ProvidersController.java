package com.tuganire.conversation;

import com.tuganire.conversation.dto.ProvidersResponse;
import com.tuganire.llm.LlmProvider;
import com.tuganire.stt.SttProvider;
import com.tuganire.tts.TtsProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller listing the registered TTS / STT / LLM providers ({@code GET /api/v1/providers}).
 *
 * <p>
 * Provider selection is not switchable at runtime: each language is routed to its provider by configuration
 * ({@code tuganire.tts.*}, {@code tuganire.stt.*}), because the right voice depends on the language, not on a global
 * preference.
 */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@Tag(name = "Providers", description = "Registered backends")
public class ProvidersController {

    private final List<TtsProvider> ttsProviders;
    private final List<SttProvider> sttProviders;
    private final List<LlmProvider> llmProviders;

    /**
     * Returns the names of all registered TTS, STT, and LLM providers.
     *
     * @return 200 with the provider names per type
     */
    @GetMapping
    @Operation(summary = "List providers", description = "Get all registered provider names per type")
    public ResponseEntity<ProvidersResponse> getProviders() {
        List<String> tts = ttsProviders.stream().map(TtsProvider::name).toList();
        List<String> stt = sttProviders.stream().map(SttProvider::name).toList();
        List<String> llm = llmProviders.stream().map(LlmProvider::name).toList();
        return ResponseEntity.ok(new ProvidersResponse(tts, stt, llm));
    }
}
