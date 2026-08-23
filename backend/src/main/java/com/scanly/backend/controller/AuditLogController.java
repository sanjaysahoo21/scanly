package com.scanly.backend.controller;

import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Audit Log Controller.
 *
 * GET /api/v1/audit-logs  — paginated list of audit logs for the user's org
 *   ?action=EDIT|APPROVE|REJECT|REPROCESS  (optional filter)
 *   ?page=0&size=20                         (pagination)
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<?> getAuditLogs(
        @AuthenticationPrincipal User currentUser,
        @RequestParam(required = false) String action,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
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
}
