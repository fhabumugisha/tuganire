package com.tuganire.tts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Records a user's A/B choice between two Kinyarwanda TTS voices (e.g. {@code "mms-pauses"} vs
 * {@code "openai-steered"}), so {@code /admin} can report which voice is preferred for Kinyarwanda over time.
 */
@Entity
@Table(name = "tts_voice_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TtsVoiceVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Variant id the user preferred (e.g. {@code "mms-pauses"}). */
    @Column(name = "chosen_variant", nullable = false, length = 100)
    private String chosenVariant;

    /** Variant id the user rejected; may be {@code null} if only one candidate was shown. */
    @Column(name = "rejected_variant", length = 100)
    private String rejectedVariant;

    /** Anonymous session id that made the choice; may be {@code null}. */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
