package com.tuganire.blog.repository;

import com.tuganire.blog.model.Article;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findBySlug(String slug);

    Page<Article> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);

    Page<Article> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    boolean existsBySlug(String slug);
}
