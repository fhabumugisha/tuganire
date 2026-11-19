package com.tuganire.stt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Guesses an audio container's file extension from its leading "magic" bytes.
 *
 * <p>
 * Browsers record in different containers ({@code webm} on Chrome/Firefox, {@code mp4} on Safari), and the OpenAI
 * transcription API relies on the uploaded file name's extension to pick a decoder. Sniffing the bytes keeps the
 * {@link SttProvider} signature unchanged (bytes only) while still sending a truthful file name.
 */
final class AudioFormatSniffer {

    static final String DEFAULT_EXTENSION = "wav";

    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final int MP4_BRAND_OFFSET = 4;
    private static final int MPEG_SYNC_MASK = 0xE0;

    private AudioFormatSniffer() {
    }

    /**
     * Returns the extension (without dot) matching the audio container, or {@link #DEFAULT_EXTENSION} when unknown.
     *
     * @param audio
     *            raw audio bytes
     * @return one of {@code webm}, {@code mp4}, {@code wav}, {@code ogg}, {@code mp3}, {@code flac}
     */
    static String extension(byte[] audio) {
        if (audio == null || audio.length < 4) {
            return DEFAULT_EXTENSION;
        }
        if (Arrays.equals(audio, 0, 4, EBML, 0, 4)) {
            return "webm";
        }
        if (startsWith(audio, 0, "RIFF")) {
            return "wav";
        }
        if (startsWith(audio, 0, "OggS")) {
            return "ogg";
        }
        if (startsWith(audio, 0, "fLaC")) {
            return "flac";
        }
        if (startsWith(audio, 0, "ID3")
                || ((audio[0] & 0xFF) == 0xFF && (audio[1] & MPEG_SYNC_MASK) == MPEG_SYNC_MASK)) {
            return "mp3";
        }
        if (audio.length >= 8 && startsWith(audio, MP4_BRAND_OFFSET, "ftyp")) {
            return "mp4";
        }
        return DEFAULT_EXTENSION;
    }

    private static boolean startsWith(byte[] audio, int offset, String ascii) {
        byte[] magic = ascii.getBytes(StandardCharsets.US_ASCII);
        return audio.length >= offset + magic.length
                && Arrays.equals(audio, offset, offset + magic.length, magic, 0, magic.length);
    }
}
