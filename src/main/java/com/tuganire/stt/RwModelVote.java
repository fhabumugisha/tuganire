package com.tuganire.stt;

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
 * Records a user's A/B choice between two models' Kinyarwanda transcript cleanups, so {@code /admin} can report which
 * model is preferred for Kinyarwanda over time.
 */
@Entity
@Table(name = "rw_model_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RwModelVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Model id the user preferred (e.g. {@code "gpt-5.5"}). */
    @Column(name = "chosen_model", nullable = false, length = 100)
    private String chosenModel;

    /** Model id the user rejected; may be {@code null} if only one candidate was shown. */
    @Column(name = "rejected_model", length = 100)
    private String rejectedModel;

    /** Anonymous session id that made the choice; may be {@code null}. */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
