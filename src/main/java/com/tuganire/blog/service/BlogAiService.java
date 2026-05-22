package com.tuganire.blog.service;

import com.tuganire.admin.service.LlmUsageTracker;
import com.tuganire.auth.model.User;
import com.tuganire.storage.StorageException;
import com.tuganire.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlogAiService {

    private static final String PROVIDER = "openai";
    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final String IMAGE_MODEL = "gpt-image-1";

    private final OpenAiChatModel chatModel;
    private final ImageModel imageModel;
    private final LlmUsageTracker usageTracker;
    private final StorageService storage;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public String generateExcerpt(String title, String content, User user) {
        ensureConfigured();
        String prompt = """
                Write a short, engaging excerpt (1-2 sentences, max 200 characters) \
                for a blog article. Match the language of the article. \
                Return only the excerpt text, no quotes, no prefix.

                Title: %s

                Content:
                %s
                """.formatted(safe(title), safe(content));

        ChatResponse response = ChatClient.create(chatModel).prompt(new Prompt(new UserMessage(prompt))).call()
                .chatResponse();

        String excerpt = response.getResult().getOutput().getText().trim();
        var usage = response.getMetadata().getUsage();
        usageTracker.track(user, PROVIDER, CHAT_MODEL, "blog.excerpt",
                usage != null ? usage.getPromptTokens().intValue() : 0,
                usage != null ? usage.getCompletionTokens().intValue() : 0);
        return excerpt;
    }

    public String generateCoverImage(String title, String excerpt, User user) {
        ensureConfigured();
        String prompt = """
                Modern, minimalist cover illustration for a blog article titled "%s". \
                Theme: %s. Clean flat design, vibrant indigo and amber accents, no text, no watermark, editorial style.
                """.formatted(safe(title), safe(excerpt));

        ImageResponse response = imageModel.call(new ImagePrompt(prompt));
        byte[] bytes = extractImageBytes(response);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Image generation returned no data");
        }

        String key = storage.buildObjectKey("blog/covers", UUID.randomUUID().toString(), "png");
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storage.uploadFile(in, key, "image/png", bytes.length);
        } catch (java.io.IOException e) {
            throw new StorageException("Failed to persist generated cover image", e);
        }

        usageTracker.track(user, PROVIDER, IMAGE_MODEL, "blog.cover", 0, 0);
        return "/files/" + key;
    }

    private byte[] extractImageBytes(ImageResponse response) {
        String b64 = response.getResult().getOutput().getB64Json();
        if (b64 != null && !b64.isBlank()) {
            return Base64.getDecoder().decode(b64);
        }
        String url = response.getResult().getOutput().getUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(60)).build();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to fetch generated image: HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch generated image from " + url, e);
        }
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }
    }

    private String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
