package com.tuganire.stt;

import java.util.List;

/**
 * Response of {@code POST /api/v1/stt/transcribe-rw/compare}: the raw MMS-ASR transcript plus one cleaned candidate per
 * compared model.
 *
 * @param raw
 *            the raw MMS-ASR transcript (lowercase, unpunctuated)
 * @param candidates
 *            the cleaned candidates, one per compared model
 */
public record RwCompareResponse(String raw, List<RwCandidate> candidates) {
}
