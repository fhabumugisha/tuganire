package com.tuganire.stt;

import com.tuganire.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Server-side speech-to-text endpoints. The browser records audio with {@code MediaRecorder} and uploads it here.
 *
 * <ul>
 * <li>{@code /transcribe-fr} — French: Whisper transcription → {@link FrenchCorrectionService} cleanup.
 * <li>{@code /transcribe-rw} — Kinyarwanda: MMS-ASR transcription.
 * <li>{@code /transcribe-rw/compare} + {@code /compare-choice} + {@code /compare-stats} — Kinyarwanda A/B mode: two
 * models clean the transcript, the user picks the better one, and the choice is recorded for per-model stats.
 * </ul>
 *
 * <p>
 * Mounted under {@code /api/v1/**} (see {@code SecurityConfig}).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stt")
@RequiredArgsConstructor
@Tag(name = "STT", description = "Server-side speech-to-text (French Whisper, Kinyarwanda MMS-ASR, A/B comparison)")
public class SttController {

    /** BCP-47 tag for French — uses Whisper + LLM correction. */
    private static final Locale FRENCH = Locale.forLanguageTag("fr");

    /** BCP-47 tag for Kinyarwanda — routed to the MMS-ASR provider. */
    private static final Locale KINYARWANDA = Locale.forLanguageTag("rw");

    /**
     * Allowlist of audio MIME types accepted by the transcription endpoints. Covers the formats produced by
     * {@code MediaRecorder} across browsers (WebM/Opus on Chrome/Firefox, MP4/AAC on Safari) plus common manual upload
     * formats (MP3, WAV, OGG, M4A).
     */
    private static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES = Set.of("audio/webm", "audio/ogg", "audio/mpeg",
            "audio/wav", "audio/mp4", "audio/x-m4a");

    private final SttService sttService;
    private final FrenchCorrectionService correctionService;
    private final RwComparisonService comparisonService;

    /**
     * Transcribes an uploaded French audio clip with Whisper, then returns both the raw transcript and an LLM-corrected
     * version.
     *
     * @param audio
     *            the recorded audio (WebM/Opus from {@code MediaRecorder}, or any format Whisper accepts); non-empty
     * @return 200 with {@link TranscriptionResponse}
     */
    @PostMapping(value = "/transcribe-fr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Transcribe French audio", description = "Whisper transcription + LLM correction of French")
    public ResponseEntity<TranscriptionResponse> transcribeFrench(@RequestParam("audio") MultipartFile audio) {
        byte[] bytes = readAudio(audio, "transcribe-fr");

        String raw = sttService.transcribe(bytes, FRENCH);
        String corrected = correctionService.correct(raw);

        return ResponseEntity.ok(new TranscriptionResponse(raw, corrected));
    }

    /**
     * Transcribes an uploaded Kinyarwanda audio clip with the MMS-ASR provider.
     *
     * <p>
     * Unlike French, there is no LLM correction step (MMS already models Kinyarwanda), so {@code corrected} mirrors
     * {@code raw}.
     *
     * @param audio
     *            the recorded audio (WebM/Opus from {@code MediaRecorder}); non-empty
     * @return 200 with {@link TranscriptionResponse}
     */
    @PostMapping(value = "/transcribe-rw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Transcribe Kinyarwanda audio", description = "MMS-ASR transcription of Kinyarwanda")
    public ResponseEntity<TranscriptionResponse> transcribeKinyarwanda(@RequestParam("audio") MultipartFile audio) {
        byte[] bytes = readAudio(audio, "transcribe-rw");

        String raw = sttService.transcribe(bytes, KINYARWANDA);

        return ResponseEntity.ok(new TranscriptionResponse(raw, raw));
    }

    /**
     * Transcribes a Kinyarwanda audio clip, then cleans it with two models for A/B comparison so the user can pick the
     * better candidate (comparison mode).
     *
     * @param audio
     *            the recorded audio; non-empty
     * @return 200 with the raw transcript and one cleaned candidate per compared model
     */
    @PostMapping(value = "/transcribe-rw/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Compare Kinyarwanda cleanups", description = "Transcribe Kinyarwanda, then clean with two models for A/B comparison")
    public ResponseEntity<RwCompareResponse> compareKinyarwanda(@RequestParam("audio") MultipartFile audio) {
        byte[] bytes = readAudio(audio, "transcribe-rw/compare");

        String raw = sttService.transcribe(bytes, KINYARWANDA);

        return ResponseEntity.ok(new RwCompareResponse(raw, comparisonService.compare(raw)));
    }

    /**
     * Records which model produced the Kinyarwanda cleanup the user preferred.
     *
     * @param request
     *            the choice payload; {@code chosenModel} is required
     * @return 200 (no body)
     */
    @PostMapping("/compare-choice")
    @Operation(summary = "Record A/B choice", description = "Record which model produced the preferred Kinyarwanda cleanup")
    public ResponseEntity<Void> recordCompareChoice(@Valid @RequestBody RwCompareChoiceRequest request) {
        comparisonService.recordChoice(request.chosenModel(), request.rejectedModel(), request.sessionId());
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the per-model A/B preference totals.
     *
     * @return 200 with one entry per model that has received at least one vote
     */
    @GetMapping("/compare-stats")
    @Operation(summary = "A/B model stats", description = "Per-model preference totals for Kinyarwanda cleanup")
    public ResponseEntity<List<RwModelStat>> compareStats() {
        return ResponseEntity.ok(comparisonService.stats());
    }

    /**
     * Validates and reads an uploaded audio file: rejects empty uploads and content types outside
     * {@link #ALLOWED_AUDIO_CONTENT_TYPES}, then loads the bytes into memory.
     *
     * @param audio
     *            the multipart upload
     * @param endpoint
     *            short endpoint name for log context
     * @return the audio bytes
     */
    private byte[] readAudio(MultipartFile audio, String endpoint) {
        if (audio == null || audio.isEmpty() || audio.getSize() == 0L) {
            throw new BusinessException("stt.audio.empty");
        }

        // Defensive content-type allowlist: reject obviously-wrong uploads before we read the bytes into memory. A
        // missing or empty Content-Type is treated as a malformed upload (some browsers send null for broken streams).
        String contentType = audio.getContentType();
        if (contentType == null || contentType.isBlank()
                || !ALLOWED_AUDIO_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            log.debug("POST /stt/{} rejected: unsupported content-type '{}'", endpoint, contentType);
            throw new BusinessException("stt.audio.unsupported-type");
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (Exception ex) {
            throw new BusinessException("stt.audio.read-failed", ex);
        }
        log.debug("POST /stt/{}: {} bytes", endpoint, bytes.length);
        return bytes;
    }

    /**
     * Response for {@code POST /api/v1/stt/transcribe-fr}.
     *
     * @param raw
     *            the raw Whisper transcript
     * @param corrected
     *            the LLM-corrected French (equal to {@code raw} if correction was unavailable)
     */
    public record TranscriptionResponse(String raw, String corrected) {
    }
}
