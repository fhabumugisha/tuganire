package com.tuganire.blog.controller;

import com.tuganire.blog.dto.ArticleSummary;
import com.tuganire.blog.mapper.ArticleMapper;
import com.tuganire.blog.model.Article;
import com.tuganire.blog.service.ArticleService;
import com.tuganire.shared.util.MarkdownRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogController {

    private static final int PAGE_SIZE = 12;

    private final ArticleService articleService;
    private final ArticleMapper articleMapper;
    private final MarkdownRenderer markdownRenderer;

    @GetMapping
    public String list(Model model, @RequestParam(defaultValue = "0") int page) {
        Page<Article> articlesPage = articleService.findPublished(page, PAGE_SIZE);
        Page<ArticleSummary> summaries = articlesPage.map(articleMapper::toSummary);
        model.addAttribute("articles", summaries);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", summaries.getTotalPages());
        return "blog/list";
    }

    @GetMapping("/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        Article article = articleService.findBySlug(slug);
        String articleHtml = markdownRenderer.renderToHtml(article.getContent());
        model.addAttribute("article", article);
        model.addAttribute("articleHtml", articleHtml);
        return "blog/detail";
    }
}
