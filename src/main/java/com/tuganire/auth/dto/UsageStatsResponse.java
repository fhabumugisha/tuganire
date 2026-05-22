package com.tuganire.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsResponse {

    private long sermonCount;
    private int maxSermons;
    private int searchCount;
    private int maxSearches;
    private int sermonPercentage;
    private int searchPercentage;
    private boolean premium;
}
