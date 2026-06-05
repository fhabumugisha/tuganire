package com.tuganire.stt;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for {@link RwModelVote} A/B choices.
 */
interface RwModelVoteRepository extends JpaRepository<RwModelVote, Long> {

    /**
     * Counts the recorded votes grouped by the chosen model.
     *
     * @return one {@link ModelVoteCount} per model that has received at least one vote
     */
    @Query("SELECT v.chosenModel AS model, COUNT(v) AS votes FROM RwModelVote v GROUP BY v.chosenModel")
    List<ModelVoteCount> countVotesByModel();

    /**
     * Projection for the per-model vote count aggregate.
     */
    interface ModelVoteCount {

        /** @return the chosen model id */
        String getModel();

        /** @return the number of times this model was chosen */
        long getVotes();
    }
}
