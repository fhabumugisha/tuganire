package com.tuganire.shared.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Names and recording helper for the {@code tuganire.pipeline.stage} timer, which measures every stage of the voice
 * conversation pipeline (STT → LLM correction + translation → TTS) so latency can be tracked as p50/p95 in Prometheus.
 *
 * <p>
 * Stages: {@link #STAGE_STT}, {@link #STAGE_LLM_FIRST_TOKEN}, {@link #STAGE_LLM_TOTAL}, {@link #STAGE_STREAM_TOTAL},
 * {@link #STAGE_TTS}. Histogram buckets are enabled in {@code application.yml}
 * ({@code management.metrics.distribution.percentiles-histogram}).
 */
public final class PipelineMetrics {

    public static final String TIMER_NAME = "tuganire.pipeline.stage";

    public static final String TAG_STAGE = "stage";
    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_MODEL = "model";
    public static final String TAG_LANG = "lang";
    public static final String TAG_OUTCOME = "outcome";

    public static final String STAGE_STT = "stt";
    public static final String STAGE_LLM_FIRST_TOKEN = "llm_first_token";
    public static final String STAGE_LLM_TOTAL = "llm_total";
    public static final String STAGE_STREAM_TOTAL = "stream_total";
    public static final String STAGE_TTS = "tts";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_GOLDEN = "golden";

    private PipelineMetrics() {
    }

    /**
     * Records one stage duration measured from {@code startNanos} ({@link System#nanoTime()}) until now.
     *
     * @param registry
     *            the meter registry
     * @param stage
     *            one of the {@code STAGE_*} constants
     * @param startNanos
     *            the {@link System#nanoTime()} value captured when the stage started
     * @param tags
     *            additional key/value tag pairs (e.g. provider, model, lang)
     */
    public static void record(MeterRegistry registry, String stage, long startNanos, String... tags) {
        Timer.builder(TIMER_NAME).tag(TAG_STAGE, stage).tags(tags).register(registry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
