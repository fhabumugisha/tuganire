package com.tuganire.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Proto.cx Voice API (native Kinyarwanda TTS).
 *
 * <p>
 * Binds from {@code tuganire.proto.*} in {@code application.yml}. All values are env-var driven and default to blank so
 * the app boots without them; when {@code subcompanyId} or {@code token} is blank the
 * {@link com.tuganire.tts.ProtoTtsProvider} transparently falls back to the local MMS voice (no failure surfaced).
 *
 * <p>
 * <strong>Secrets:</strong> {@code token} is a Proto "takeover secret" and {@code subcompanyId} the teamspace id — both
 * come from {@code PROTO_TTS_TOKEN} / {@code PROTO_TTS_SUBCOMPANY_ID} and must never be committed.
 *
 * @param baseUrl
 *            Proto API base URL (e.g. {@code https://v3-api.proto.cx/api})
 * @param subcompanyId
 *            Proto teamspace id used in the TTS path; blank disables the provider (→ MMS fallback)
 * @param token
 *            Proto takeover secret sent as a Bearer token; blank disables the provider (→ MMS fallback)
 * @param gender
 *            requested voice gender ({@code "female"} or {@code "male"})
 */
@ConfigurationProperties(prefix = "tuganire.proto")
public record ProtoTtsConfig(String baseUrl, String subcompanyId, String token, String gender) {

    /**
     * Returns {@code true} when both the teamspace id and token are present, i.e. the Proto provider can be called.
     *
     * @return {@code true} when fully configured
     */
    public boolean isConfigured() {
        return subcompanyId != null && !subcompanyId.isBlank() && token != null && !token.isBlank();
    }
}
