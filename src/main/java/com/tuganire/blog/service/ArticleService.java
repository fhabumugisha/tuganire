package com.tuganire.blog.service;

import com.tuganire.auth.model.User;
import com.tuganire.blog.dto.ArticleForm;
import com.tuganire.blog.model.Article;
import org.springframework.data.domain.Page;

public interface ArticleService {

    Page<Article> findPublished(int page, int size);

    Article findBySlug(String slug);

    Page<Article> findAllForAdmin(int page, int size);

    Article findByIdForAdmin(Long id);

    Article create(ArticleForm form, User author);

    Article update(Long id, ArticleForm form);

    void delete(Long id);

    void togglePublished(Long id);
}
