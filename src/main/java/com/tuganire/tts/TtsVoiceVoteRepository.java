package com.tuganire.tts;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for {@link TtsVoiceVote} A/B voice choices.
 */
interface TtsVoiceVoteRepository extends JpaRepository<TtsVoiceVote, Long> {

    /**
     * Counts the recorded votes grouped by the chosen voice variant.
     *
     * @return one {@link VariantVoteCount} per variant that has received at least one vote
     */
    @Query("SELECT v.chosenVariant AS variant, COUNT(v) AS votes FROM TtsVoiceVote v GROUP BY v.chosenVariant")
    List<VariantVoteCount> countVotesByVariant();

    /**
     * Projection for the per-variant vote count aggregate.
     */
    interface VariantVoteCount {

        /** @return the chosen variant id */
        String getVariant();

        /** @return the number of times this variant was chosen */
        long getVotes();
    }
}
