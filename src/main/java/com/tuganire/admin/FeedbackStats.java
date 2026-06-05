package com.tuganire.admin;

import java.util.List;

/**
 * Aggregated feedback data for the admin dashboard.
 *
 * @param thumbsUp
 *            count of 👍 feedback
 * @param thumbsDown
 *            count of 👎 feedback
 * @param corrections
 *            count of proposed corrections
 * @param recent
 *            most recent feedback rows, newest first
 */
public record FeedbackStats(long thumbsUp, long thumbsDown, long corrections, List<FeedbackRow> recent) {

    /**
     * Total number of feedback entries across all types.
     *
     * @return the sum of all feedback counts
     */
    public long total() {
        return thumbsUp + thumbsDown + corrections;
    }
}
