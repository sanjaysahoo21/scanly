package com.scanly.backend.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Dashboard statistics DTO.
 * Returned by GET /api/v1/dashboard/stats.
 */
@Getter
@Builder
public class DashboardStatsDto {
    private long totalDocuments;
    private long pending;
    private long processing;
    private long completed;
    private long failed;
    private long needsReview;
    // Last 5 recent documents
    private java.util.List<com.scanly.backend.entity.Document> recentDocuments;
}
