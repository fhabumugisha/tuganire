package com.tuganire.feedback;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepo extends JpaRepository<Feedback, Long> {

    long countByType(FeedbackType type);

    List<Feedback> findAllByOrderByCreatedAtDesc(Limit limit);
}
