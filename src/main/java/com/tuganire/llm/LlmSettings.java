package com.tuganire.llm;

import com.tuganire.config.LlmConfig;
import com.tuganire.config.LlmConfig.ModelOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Holds runtime-tunable LLM parameters shared across the application (ADR-004 style: switchable at runtime, no
 * restart).
 *
 * <p>
 * Two knobs are exposed in settings so FR→RW translation quality can be compared without a redeploy:
 * <ul>
 * <li>the translation <b>temperature</b> (lower = more faithful);
 * <li>the translation <b>model</b>, chosen from {@code tuganire.llm.translation-models} — possibly across providers
 * (e.g. {@code gpt-5.5} on OpenAI vs {@code claude-sonnet-4-6} on Anthropic). The selected model also determines which
 * provider the pipeline routes to (see {@link #getActiveProvider()}).
 * </ul>
 * Both are process-global (one value for everyone), matching the {@code activeTtsProvider} pattern — tuning knobs, not
 * per-user preferences. Providers read them on every call so a change takes effect on the next translation.
 */
@Component
public class LlmSettings {

    /** Default GPT translation temperature when nothing has been set (Spring AI 2.0 has no default). */
    public static final double DEFAULT_TEMPERATURE = 0.3;

    /** Lower bound — fully deterministic. */
    public static final double MIN_TEMPERATURE = 0.0;

    /** Upper bound — providers accept higher, but values above 1.0 are unhelpful for translation. */
    public static final double MAX_TEMPERATURE = 1.0;

    private final AtomicReference<Double> translationTemperature = new AtomicReference<>(DEFAULT_TEMPERATURE);

    private final List<ModelOption> availableModels;
    private final AtomicReference<ModelOption> activeModel;

    public LlmSettings(LlmConfig config) {
        this.availableModels = List.copyOf(config.translationModels());
        // Fail fast: a typo in `tuganire.llm.default-translation-model` must block startup so the operator
        // cannot ship a misconfigured app silently falling back to the first model in the list.
        ModelOption initial = this.availableModels.stream().filter(m -> m.id().equals(config.defaultTranslationModel()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("default-translation-model '"
                        + config.defaultTranslationModel() + "' is not in tuganire.llm.translation-models: "
                        + this.availableModels.stream().map(ModelOption::id).toList()));
        this.activeModel = new AtomicReference<>(initial);
    }

    /** @return the current translation temperature */
    public double getTemperature() {
        return translationTemperature.get();
    }

    /**
     * Sets the translation temperature, clamped to {@code [MIN_TEMPERATURE, MAX_TEMPERATURE]}.
     *
     * <p>
     * The clamp is kept as defence-in-depth; callers should validate the input first (e.g. the
     * {@code SettingsController} rejects out-of-range values with 400 via bean validation) so a clamped value is never
     * silently accepted from the API.
     *
     * @param temperature
     *            the requested temperature
     * @return the value actually stored after clamping
     */
    public double setTemperature(double temperature) {
        double clamped = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, temperature));
        translationTemperature.set(clamped);
        return clamped;
    }

    /** @return the id of the model currently used for translation (e.g. {@code "gpt-5.5"}) */
    public String getModel() {
        return activeModel.get().id();
    }

    /** @return the provider name that owns the active model (e.g. {@code "openai"}, {@code "anthropic"}) */
    public String getActiveProvider() {
        return activeModel.get().provider();
    }

    /** @return the immutable list of selectable models (from configuration) */
    public List<ModelOption> getAvailableModels() {
        return availableModels;
    }

    /**
     * Returns whether {@code modelId} is one of the configured selectable models.
     *
     * @param modelId
     *            candidate model id
     * @return {@code true} if selectable
     */
    public boolean isModelAllowed(String modelId) {
        return availableModels.stream().anyMatch(m -> m.id().equals(modelId));
    }

    /**
     * Sets the active translation model by id.
     *
     * @param modelId
     *            an allowed model id
     * @throws IllegalArgumentException
     *             if {@code modelId} is not one of the configured selectable models (no silent no-op)
     */
    public void setModel(String modelId) {
        ModelOption next = availableModels.stream().filter(m -> m.id().equals(modelId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Model not allowed: " + modelId));
        activeModel.set(next);
    }
}
