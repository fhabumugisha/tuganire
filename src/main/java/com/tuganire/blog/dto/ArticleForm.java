package com.tuganire.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleForm {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
    private String title;

    @Size(max = 255, message = "Le slug ne doit pas dépasser 255 caractères")
    private String slug;

    @Size(max = 5000, message = "L'extrait ne doit pas dépasser 5000 caractères")
    private String excerpt;

    @NotBlank(message = "Le contenu est obligatoire")
    private String content;

    @Size(max = 500, message = "L'URL de couverture ne doit pas dépasser 500 caractères")
    private String coverImageUrl;

    private boolean published;
}
