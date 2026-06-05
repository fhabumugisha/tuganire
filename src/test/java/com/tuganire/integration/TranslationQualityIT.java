package com.tuganire.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuganire.llm.LlmProvider;
import com.tuganire.llm.LlmProviderFactory;
import com.tuganire.postprocessor.KinyarwandaPostProcessor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Semantic regression gate for the post-processor pipeline.
 *
 * <p>
 * Loads the 68-phrase {@code llm-errors-corpus.csv}, feeds each LLM raw output through
 * {@link KinyarwandaPostProcessor}, and compares the result against the native correction. If the aggregate score
 * regresses more than 5% versus the committed baseline in {@code quality-baseline.properties}, the build fails.
 *
 * <p>
 * The LLM is never called — the corpus contains pre-recorded raw LLM outputs. This makes the test deterministic and
 * independent of network availability (ADR-005: quality is the product).
 */
class TranslationQualityIT extends AbstractIntegrationTest {

    private static final Locale FR = Locale.forLanguageTag("fr");
    private static final Locale RW = Locale.forLanguageTag("rw");
    private static final double MAX_REGRESSION_RATIO = 0.05;

    @Autowired
    private KinyarwandaPostProcessor postProcessor;

    @MockitoBean
    private LlmProviderFactory llmProviderFactory;

    @MockitoBean
    private LlmProvider stubbedLlmProvider;

    @BeforeEach
    void setUpLlmStub() {
        Mockito.when(stubbedLlmProvider.name()).thenReturn("openai");
        Mockito.when(llmProviderFactory.getDefault()).thenReturn(stubbedLlmProvider);
        Mockito.when(llmProviderFactory.getFallback()).thenReturn(stubbedLlmProvider);
    }

    @Test
    void postProcessorQuality_doesNotRegressMoreThanFivePercent() throws IOException {
        // Given — load corpus and baseline
        List<CorpusEntry> corpus = loadCorpus();
        double baselineScore = loadBaselineScore();
        int minCorpusSize = loadBaselineCorpusSize();

        assertThat(corpus).hasSizeGreaterThanOrEqualTo(minCorpusSize);

        // When — run every corpus entry through the post-processor (no real LLM called)
        int correct = 0;
        List<String> failures = new ArrayList<>();
        for (CorpusEntry entry : corpus) {
            // null source → register detection off, all rules apply (this corpus tests RW output quality).
            String processed = postProcessor.process(entry.llmRawOutput(), null, FR, RW).text();
            if (matchesNativeCorrection(processed, entry.nativeCorrection())) {
                correct++;
            } else {
                failures.add(String.format("[id=%s] raw='%s' expected='%s' got='%s'", entry.id(), entry.llmRawOutput(),
                        entry.nativeCorrection(), processed));
            }
        }

        // Then — aggregate score must not regress > 5% vs baseline
        double actualScore = (double) correct / corpus.size();
        double minimumAcceptableScore = baselineScore - MAX_REGRESSION_RATIO;

        assertThat(actualScore)
                .as("Quality score %.3f is below minimum %.3f. Failing entries:%n%s", actualScore,
                        minimumAcceptableScore, String.join("\n", failures))
                .isGreaterThanOrEqualTo(minimumAcceptableScore);
    }

    private static boolean matchesNativeCorrection(String processed, String nativeCorrection) {
        return processed.trim().equalsIgnoreCase(nativeCorrection.trim());
    }

    private static List<CorpusEntry> loadCorpus() throws IOException {
        List<CorpusEntry> entries = new ArrayList<>();
        try (InputStream is = TranslationQualityIT.class.getResourceAsStream("/test-data/llm-errors-corpus.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            // Skip header row
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", 6);
                if (cols.length >= 6) {
                    entries.add(new CorpusEntry(cols[0].trim(), cols[1].trim(), cols[2].trim(), cols[3].trim(),
                            cols[4].trim(), cols[5].trim()));
                }
            }
        }
        return entries;
    }

    private static double loadBaselineScore() throws IOException {
        return Double.parseDouble(loadBaselineProperties().getProperty("baseline.score", "0.95"));
    }

    private static int loadBaselineCorpusSize() throws IOException {
        return Integer.parseInt(loadBaselineProperties().getProperty("baseline.corpus.size", "65"));
    }

    private static Properties loadBaselineProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream is = TranslationQualityIT.class
                .getResourceAsStream("/test-data/quality-baseline.properties")) {
            props.load(is);
        }
        return props;
    }

    private record CorpusEntry(String id, String sourceText, String sourceLanguage, String llmRawOutput,
            String nativeCorrection, String category) {
    }
}
