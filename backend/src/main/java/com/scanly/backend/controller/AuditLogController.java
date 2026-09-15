package com.scanly.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.repository.AuditLogRepository;
import com.scanly.backend.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Audit Log Controller.
 *
 * GET /api/v1/audit-logs              — paginated list of audit logs for the user's org
 *   ?action=EDIT|APPROVE|REJECT|REPROCESS  (optional filter)
 *   ?page=0&size=20                        (pagination)
 * GET /api/v1/audit-logs/export       — download all audit logs as CSV or JSON
 *   ?format=csv|json  (default: csv)
 *   ?action=EDIT|...  (optional filter)
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final ExportService exportService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<?> getAuditLogs(
        @AuthenticationPrincipal User currentUser,
        @RequestParam(required = false) String action,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PAGINATION",
                "message", "page must be non-negative and size must be 1-100"));
        }
        var org = currentUser.getOrganization();
        Pageable pageable = PageRequest.of(page, size);

        Page<AuditLog> result;
        if (action != null && !action.isBlank()) {
            try {
                AuditAction auditAction = AuditAction.valueOf(action.toUpperCase());
                result = auditLogRepository.findByOrganizationAndAction(org, auditAction, pageable);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_ACTION", "message", "Unknown action: " + action));
            }
        } else {
            result = auditLogRepository.findByOrganization(org, pageable);
        }

        return ResponseEntity.ok(Map.of(
            "logs", result.getContent(),
            "totalElements", result.getTotalElements(),
            "totalPages", result.getTotalPages(),
            "currentPage", result.getNumber()
        ));
    }

    /**
     * Export audit logs as CSV or JSON.
     * GET /api/v1/audit-logs/export?format=csv&action=EDIT
     */
    @GetMapping("/export")
    public ResponseEntity<?> exportLogs(
        @AuthenticationPrincipal User currentUser,
        @RequestParam(required = false) String action,
        @RequestParam(defaultValue = "csv") String format
    ) {
        var org = currentUser.getOrganization();
        AuditAction actionFilter = null;
        if (action != null && !action.isBlank()) {
            try {
                actionFilter = AuditAction.valueOf(action.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_ACTION", "message", "Unknown action: " + action));
            }
        }

        if ("json".equalsIgnoreCase(format)) {
            List<AuditLog> logs = fetchAll(org, actionFilter);
            try {
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(logs);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }

        byte[] csv = exportService.exportAuditLogsCsv(org, actionFilter);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    private List<AuditLog> fetchAll(com.scanly.backend.entity.Organization org, AuditAction actionFilter) {
        Pageable all = PageRequest.of(0, 10_000);
        if (actionFilter != null) {
            return auditLogRepository.findByOrganizationAndAction(org, actionFilter, all).getContent();
        }
        return auditLogRepository.findByOrganization(org, all).getContent();
    }
}

