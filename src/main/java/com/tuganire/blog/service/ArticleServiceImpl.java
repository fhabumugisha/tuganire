package com.tuganire.blog.service;

import com.tuganire.auth.model.User;
import com.tuganire.blog.dto.ArticleForm;
import com.tuganire.blog.model.Article;
import com.tuganire.blog.repository.ArticleRepository;
import com.tuganire.shared.exception.ResourceNotFoundException;
import com.tuganire.shared.util.SlugGenerator;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Article> findPublished(int page, int size) {
        return articleRepository.findByPublishedTrueOrderByPublishedAtDesc(PageRequest.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public Article findBySlug(String slug) {
        return articleRepository.findBySlug(slug).filter(Article::isPublished)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Article> findAllForAdmin(int page, int size) {
        return articleRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public Article findByIdForAdmin(Long id) {
        return articleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Article", id));
    }

    @Override
    @Transactional
    public Article create(ArticleForm form, User author) {
        String slug = resolveSlug(form.getSlug(), form.getTitle(), null);

        Article article = Article.builder().title(form.getTitle()).slug(slug).excerpt(form.getExcerpt())
                .content(form.getContent()).coverImageUrl(form.getCoverImageUrl()).published(form.isPublished())
                .author(author).publishedAt(form.isPublished() ? LocalDateTime.now() : null).build();

        return articleRepository.save(article);
    }

    @Override
    @Transactional
    public Article update(Long id, ArticleForm form) {
        Article article = findByIdForAdmin(id);

        String slug = resolveSlug(form.getSlug(), form.getTitle(), article.getSlug());

        boolean wasPublished = article.isPublished();
        boolean nowPublished = form.isPublished();

        article.setTitle(form.getTitle());
        article.setSlug(slug);
        article.setExcerpt(form.getExcerpt());
        article.setContent(form.getContent());
        article.setCoverImageUrl(form.getCoverImageUrl());
        article.setPublished(nowPublished);

        if (!wasPublished && nowPublished) {
            article.setPublishedAt(LocalDateTime.now());
        } else if (wasPublished && !nowPublished) {
            article.setPublishedAt(null);
        }

        return articleRepository.save(article);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Article article = findByIdForAdmin(id);
        articleRepository.delete(article);
    }

    @Override
    @Transactional
    public void togglePublished(Long id) {
        Article article = findByIdForAdmin(id);
        boolean nowPublished = !article.isPublished();
        article.setPublished(nowPublished);
        if (nowPublished) {
            article.setPublishedAt(LocalDateTime.now());
        } else {
            article.setPublishedAt(null);
        }
        articleRepository.save(article);
    }

    private String resolveSlug(String formSlug, String title, String existingSlug) {
        String base;
        if (formSlug != null && !formSlug.isBlank()) {
            base = SlugGenerator.toKebabCase(formSlug.trim(), 80);
        } else {
            base = SlugGenerator.toKebabCase(title, 80);
        }

        if (base.isEmpty()) {
            base = "article";
        }

        if (base.equals(existingSlug)) {
            return existingSlug;
        }

        if (!articleRepository.existsBySlug(base)) {
            return base;
        }

        for (int i = 2; i <= 999; i++) {
            String candidate = base + "-" + i;
            if (!articleRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }

        return base + "-" + System.currentTimeMillis();
    }
}
