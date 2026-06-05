package com.tuganire.conversation;

import com.tuganire.conversation.dto.ModelOptionDto;
import com.tuganire.conversation.dto.TemperatureRequest;
import com.tuganire.conversation.dto.TemperatureResponse;
import com.tuganire.conversation.dto.TranslationModelRequest;
import com.tuganire.conversation.dto.TranslationModelResponse;
import com.tuganire.llm.LlmSettings;
import com.tuganire.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for runtime-tunable LLM settings (translation temperature and active translation model).
 *
 * <ul>
 * <li>{@code GET/PUT /api/v1/settings/temperature} — view/change the temperature passed to the active LLM model.
 * <li>{@code GET/PUT /api/v1/settings/translation-model} — view/change the cross-provider translation model.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "LLM Settings", description = "Runtime-tunable LLM parameters")
public class SettingsController {

    private final LlmSettings llmSettings;

    /**
     * Returns the current temperature used by the active LLM translation model and its allowed range.
     *
     * @return 200 with the temperature settings
     */
    @GetMapping("/temperature")
    @Operation(summary = "Get translation temperature", description = "Current temperature used by the active LLM model and its range")
    public ResponseEntity<TemperatureResponse> getTemperature() {
        return ResponseEntity.ok(new TemperatureResponse(llmSettings.getTemperature(), LlmSettings.MIN_TEMPERATURE,
                LlmSettings.MAX_TEMPERATURE, LlmSettings.DEFAULT_TEMPERATURE));
    }

    /**
     * Sets the translation temperature at runtime so quality can be compared across values across providers/models.
     * Out-of-range values are rejected with 400 by bean validation on {@link TemperatureRequest}; the stored value is
     * returned for confirmation.
     *
     * @param request
     *            the temperature payload
     * @return 200 with the updated temperature settings
     */
    @PutMapping("/temperature")
    @Operation(summary = "Set translation temperature", description = "Change the temperature passed to the active LLM model")
    public ResponseEntity<TemperatureResponse> setTemperature(@Valid @RequestBody TemperatureRequest request) {
        double stored = llmSettings.setTemperature(request.temperature());
        log.info("PUT /settings/temperature: set translation temperature to {}", stored);
        return ResponseEntity.ok(new TemperatureResponse(stored, LlmSettings.MIN_TEMPERATURE,
                LlmSettings.MAX_TEMPERATURE, LlmSettings.DEFAULT_TEMPERATURE));
    }

    /**
     * Returns the active FR→RW translation model and the list of selectable models.
     *
     * @return 200 with the model settings
     */
    @GetMapping("/translation-model")
    @Operation(summary = "Get translation model", description = "Active model and selectable options (cross-provider)")
    public ResponseEntity<TranslationModelResponse> getTranslationModel() {
        return ResponseEntity.ok(new TranslationModelResponse(llmSettings.getModel(), modelOptions()));
    }

    /**
     * Switches the model used for FR→RW translation at runtime so quality can be compared across models and providers.
     * The selected model also determines which provider runs (e.g. a {@code claude-*} model routes to Anthropic). An
     * unknown model (not in {@code tuganire.llm.translation-models}) throws a {@link BusinessException}.
     *
     * @param request
     *            the model switch payload
     * @return 200 with the updated model settings
     */
    @PutMapping("/translation-model")
    @Operation(summary = "Set translation model", description = "Change the model used for translation")
    public ResponseEntity<TranslationModelResponse> setTranslationModel(
            @Valid @RequestBody TranslationModelRequest request) {
        if (!llmSettings.isModelAllowed(request.model())) {
            throw new BusinessException("llm.model.not-found");
        }
        try {
            llmSettings.setModel(request.model());
        } catch (IllegalArgumentException ex) {
            // Defence in depth: LlmSettings.setModel also rejects unknown ids (C6). Translate to a localised
            // BusinessException so the client sees a 400 with the user-facing message, not the raw exception text.
            throw new BusinessException("llm.model.not-found", ex);
        }
        log.info("PUT /settings/translation-model: set translation model to '{}'", request.model());
        return ResponseEntity.ok(new TranslationModelResponse(llmSettings.getModel(), modelOptions()));
    }

    private List<ModelOptionDto> modelOptions() {
        return llmSettings.getAvailableModels().stream().map(m -> new ModelOptionDto(m.id(), m.label(), m.provider()))
                .toList();
    }
}
