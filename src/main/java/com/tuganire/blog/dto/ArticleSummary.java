package com.tuganire.blog.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSummary {

    private Long id;
    private String slug;
    private String title;
    private String excerpt;
    private String coverImageUrl;
    private LocalDateTime publishedAt;
    private String authorName;
    private int readingTimeMinutes;
}
