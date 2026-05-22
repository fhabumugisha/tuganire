package com.tuganire.blog.controller;

import com.tuganire.auth.model.User;
import com.tuganire.blog.dto.ArticleForm;
import com.tuganire.blog.model.Article;
import com.tuganire.blog.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/blog")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminBlogController {

    private static final int PAGE_SIZE = 20;
    private static final String FORM_VIEW = "admin/blog/form";
    private static final String REDIRECT_LIST = "redirect:/admin/blog";

    private final ArticleService articleService;

    @GetMapping
    public String list(Model model, @RequestParam(defaultValue = "0") int page) {
        Page<Article> articlesPage = articleService.findAllForAdmin(page, PAGE_SIZE);
        model.addAttribute("articles", articlesPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", articlesPage.getTotalPages());
        model.addAttribute("activeNav", "articles");
        return "admin/blog/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("articleForm", new ArticleForm());
        model.addAttribute("isNew", true);
        model.addAttribute("activeNav", "articles");
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid ArticleForm articleForm, BindingResult bindingResult,
            @AuthenticationPrincipal User currentUser, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isNew", true);
            model.addAttribute("activeNav", "articles");
            return FORM_VIEW;
        }
        Article created = articleService.create(articleForm, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Article créé avec succès.");
        return "redirect:/admin/blog/" + created.getId() + "/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Article article = articleService.findByIdForAdmin(id);
        ArticleForm form = ArticleForm.builder().title(article.getTitle()).slug(article.getSlug())
                .excerpt(article.getExcerpt()).content(article.getContent()).coverImageUrl(article.getCoverImageUrl())
                .published(article.isPublished()).build();
        model.addAttribute("articleForm", form);
        model.addAttribute("article", article);
        model.addAttribute("isNew", false);
        model.addAttribute("activeNav", "articles");
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid ArticleForm articleForm, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Article article = articleService.findByIdForAdmin(id);
            model.addAttribute("article", article);
            model.addAttribute("isNew", false);
            model.addAttribute("activeNav", "articles");
            return FORM_VIEW;
        }
        articleService.update(id, articleForm);
        redirectAttributes.addFlashAttribute("successMessage", "Article mis à jour avec succès.");
        return "redirect:/admin/blog/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        articleService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Article supprimé avec succès.");
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/toggle-published")
    public String togglePublished(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        articleService.togglePublished(id);
        redirectAttributes.addFlashAttribute("successMessage", "Statut de publication mis à jour.");
        return REDIRECT_LIST;
    }
}
