package com.tuganire.llm;

import com.tuganire.admin.LlmUsageTracker;
import com.tuganire.llm.prompt.TranslationPromptBuilder;
import com.tuganire.translation.StructuredTranslation;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Shared base for {@link LlmProvider} implementations backed by Spring AI's {@link ChatClient}.
 *
 * <p>
 * Captures the parts that were duplicated between {@code OpenAiLlmProvider} and {@code ClaudeLlmProvider}: the
 * {@link ChatClient} held with the translation system prompt, the per-call prompt construction, the response-text
 * extraction, and the {@link LlmUsageTracker} token-usage bookkeeping. Subclasses only supply provider-specific bits:
 * the provider {@link #name()} and the per-call {@link ChatOptions.Builder} (which model id and which temperature, if
 * any, to apply for that model).
 */
@Slf4j
public abstract class AbstractChatClientLlmProvider implements LlmProvider {

    /** Feature tag used for {@link LlmUsageTracker} bookkeeping — all subclasses serve translation. */
    protected static final String FEATURE_TRANSLATION = "translation";

    protected final ChatClient chatClient;
    protected final TranslationPromptBuilder promptBuilder;
    protected final LlmUsageTracker usageTracker;

    protected AbstractChatClientLlmProvider(ChatClient.Builder chatClientBuilder,
            TranslationPromptBuilder promptBuilder, LlmUsageTracker usageTracker) {
        this.promptBuilder = promptBuilder;
        this.usageTracker = usageTracker;
        this.chatClient = chatClientBuilder.defaultSystem(promptBuilder.buildSystemPrompt()).build();
    }

    /**
     * Builds the per-call {@link ChatOptions.Builder} for the given model. Subclasses decide whether to apply the
     * configured temperature, whether to use a provider-specific options type (e.g.
     * {@code AnthropicChatOptions.builder()}), and any other model-aware tweaks.
     *
     * @param modelId
     *            model id being called
     * @return an options builder already carrying at least the model id
     */
    protected abstract ChatOptions.Builder<?> buildOptions(String modelId);

    @Override
    public final String translate(String text, Locale src, Locale tgt, String modelId) {
        log.debug("{} translate: {} → {}, model={}, text length={}", name(), src, tgt, modelId, text.length());
        String userPrompt = promptBuilder.buildUserPrompt(text, src, tgt);
        ChatResponse response = chatClient.prompt().user(userPrompt).options(buildOptions(modelId)).call()
                .chatResponse();
        recordUsage(modelId, response);
        String result = response != null && response.getResult() != null
                ? response.getResult().getOutput().getText()
                : null;
        log.debug("{} translation result length={}", name(), result == null ? 0 : result.length());
        return result != null ? result : "";
    }

    @Override
    public StructuredTranslation translateStructured(String text, Locale src, Locale tgt, String modelId) {
        log.debug("{} translateStructured: {} → {}, model={}, text length={}", name(), src, tgt, modelId,
                text.length());
        String userPrompt = promptBuilder.buildUserPrompt(text, src, tgt);
        ResponseEntity<ChatResponse, StructuredTranslation> entity = chatClient.prompt().user(userPrompt)
                .options(buildOptions(modelId)).call().responseEntity(StructuredTranslation.class);
        recordUsage(modelId, entity.response());
        return entity.entity();
    }

    /**
     * Records token usage from the chat response; best-effort (tracker never throws).
     *
     * @param modelId
     *            the model the call was routed to
     * @param response
     *            the raw {@link ChatResponse} from Spring AI (may be {@code null} if the SDK returned nothing)
     */
    protected final void recordUsage(String modelId, @Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        usageTracker.track(name(), modelId, FEATURE_TRANSLATION, promptTokens, completionTokens);
    }
}
