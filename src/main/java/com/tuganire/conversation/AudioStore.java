package com.tuganire.conversation;

import java.util.Optional;

/**
 * Ephemeral store for synthesised audio bytes.
 *
 * <p>
 * Entries are keyed by a generated id and expire after a short TTL (5 minutes). The store is deliberately not persisted
 * to disk or DB (ARCHI section 10) — its sole purpose is to serve audio bytes via the {@code GET
 * /api/v1/audio/{id}.mp3} endpoint between the time a pipeline run completes and the client fetches the result.
 */
public interface AudioStore {

    /**
     * Stores {@code audioBytes} under the given {@code id} with a 5-minute TTL.
     *
     * @param id
     *            unique identifier for this audio clip; non-null
     * @param audioBytes
     *            raw audio bytes (MP3); non-null, non-empty
     */
    void store(String id, byte[] audioBytes);

    /**
     * Retrieves the audio bytes for the given {@code id}, or empty if the entry has expired or never existed.
     *
     * @param id
     *            the identifier returned by a previous {@link #store} call
     * @return an {@link Optional} containing the audio bytes, or empty
     */
    Optional<byte[]> getAudio(String id);
}
