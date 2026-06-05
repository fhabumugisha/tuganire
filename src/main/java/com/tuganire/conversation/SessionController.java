package com.tuganire.conversation;

import com.tuganire.conversation.dto.LanguageInfo;
import com.tuganire.conversation.dto.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for anonymous session lifecycle and language metadata.
 *
 * <p>
 * Provider listing and runtime settings (TTS / temperature / translation model) live in their own controllers — see
 * {@link ProvidersController} and {@link SettingsController}. The endpoint URLs are unchanged across the split (the web
 * POC and Android app depend on the stable paths).
 *
 * <ul>
 * <li>{@code POST /api/v1/sessions} — issues an anonymous session UUID.
 * <li>{@code GET /api/v1/languages} — returns the supported language pairs.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
@Tag(name = "Sessions & Meta", description = "Session lifecycle and language metadata")
public class SessionController {

    /** BCP-47 codes supported in the MVP (FR ↔ RW). */
    private static final List<LanguageInfo> SUPPORTED_LANGUAGES = List.of(new LanguageInfo("fr", "Français", "French"),
            new LanguageInfo("rw", "Ikinyarwanda", "Kinyarwanda"));

    /**
     * Creates a new anonymous session and returns its UUID plus the WebSocket upgrade URL.
     *
     * @return 200 with session ID and websocket URL
     */
    @PostMapping("/sessions")
    @Operation(summary = "Create session", description = "Create an anonymous conversation session")
    public ResponseEntity<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        String websocketUrl = "/ws/conversation/" + sessionId;
        log.debug("POST /sessions: created sessionId={}", sessionId);
        return ResponseEntity.ok(new SessionResponse(sessionId, websocketUrl));
    }

    /**
     * Returns the list of languages supported by the translation pipeline.
     *
     * @return 200 with the language list
     */
    @GetMapping("/languages")
    @Operation(summary = "List languages", description = "Get all supported source/target language codes")
    public ResponseEntity<List<LanguageInfo>> getLanguages() {
        return ResponseEntity.ok(SUPPORTED_LANGUAGES);
    }
}
