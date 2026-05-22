package com.tuganire.blog.controller;

import com.tuganire.auth.model.User;
import com.tuganire.blog.service.BlogAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/blog/ai")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminBlogAiController {

    private final BlogAiService blogAiService;

    @PostMapping(value = "/generate-excerpt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> generateExcerpt(@RequestParam("title") String title,
            @RequestParam("content") String content, @AuthenticationPrincipal User currentUser) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body("Titre et contenu requis.");
        }
        try {
            String excerpt = blogAiService.generateExcerpt(title, content, currentUser);
            return ResponseEntity.ok(excerpt);
        } catch (IllegalStateException e) {
            log.warn("AI excerpt generation unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).body(e.getMessage());
        } catch (Exception e) {
            log.error("AI excerpt generation failed", e);
            return ResponseEntity.internalServerError().body("Erreur lors de la génération.");
        }
    }

    @PostMapping(value = "/generate-cover", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> generateCover(@RequestParam("title") String title,
            @RequestParam(value = "excerpt", required = false) String excerpt,
            @AuthenticationPrincipal User currentUser) {
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body("Titre requis.");
        }
        try {
            String url = blogAiService.generateCoverImage(title, excerpt != null ? excerpt : "", currentUser);
            return ResponseEntity.ok(url);
        } catch (IllegalStateException e) {
            log.warn("AI cover generation unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).body(e.getMessage());
        } catch (Exception e) {
            log.error("AI cover generation failed", e);
            return ResponseEntity.internalServerError().body("Erreur lors de la génération.");
        }
    }
}
