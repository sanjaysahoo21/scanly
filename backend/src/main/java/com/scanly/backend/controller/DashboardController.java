package com.scanly.backend.controller;

import com.scanly.backend.dto.DashboardStatsDto;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.DocumentStatus;
import com.scanly.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard Controller.
 *
 * GET /api/v1/dashboard/stats — returns document counts and recent files
 *   for the logged-in user's organization.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DocumentRepository documentRepository;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats(
        @AuthenticationPrincipal User currentUser
    ) {
        var org = currentUser.getOrganization();

        DashboardStatsDto stats = DashboardStatsDto.builder()
            .totalDocuments(documentRepository.countByOrganization(org))
            .pending(documentRepository.countByOrganizationAndStatus(org, DocumentStatus.PENDING))
            .processing(documentRepository.countByOrganizationAndStatus(org, DocumentStatus.PROCESSING))
            .completed(documentRepository.countByOrganizationAndStatus(org, DocumentStatus.COMPLETED))
            .failed(documentRepository.countByOrganizationAndStatus(org, DocumentStatus.FAILED))
            .needsReview(documentRepository.countByOrganizationAndStatus(org, DocumentStatus.NEEDS_REVIEW))
            .recentDocuments(documentRepository.findTop5ByOrganizationOrderByCreatedAtDesc(org))
            .build();

        return ResponseEntity.ok(stats);
    }
}
