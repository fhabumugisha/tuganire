package com.tuganire.blog.mapper;

import com.tuganire.blog.dto.ArticleSummary;
import com.tuganire.blog.model.Article;
import org.springframework.stereotype.Component;

@Component
public class ArticleMapper {

    public ArticleSummary toSummary(Article article) {
        if (article == null) {
            return null;
        }

        String authorName = "";
        if (article.getAuthor() != null) {
            authorName = article.getAuthor().getFirstName() + " " + article.getAuthor().getLastName();
        }

        return ArticleSummary.builder().id(article.getId()).slug(article.getSlug()).title(article.getTitle())
                .excerpt(article.getExcerpt()).coverImageUrl(article.getCoverImageUrl())
                .publishedAt(article.getPublishedAt()).authorName(authorName.trim())
                .readingTimeMinutes(article.getReadingTimeMinutes()).build();
    }
}
