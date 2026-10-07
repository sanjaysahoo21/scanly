package com.scanly.backend.controller;

import com.scanly.backend.dto.IntegrationRequest;
import com.scanly.backend.dto.IntegrationResponse;
import com.scanly.backend.entity.IntegrationConfig;
import com.scanly.backend.entity.IntegrationPushLog;
import com.scanly.backend.entity.User;
import com.scanly.backend.service.IntegrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IntegrationController — Part 1 (Foundation).
 *
 * GET    /api/v1/integrations                  — list integrations for org
 * POST   /api/v1/integrations                  — create new integration
 * PUT    /api/v1/integrations/{id}             — update integration
 * DELETE /api/v1/integrations/{id}             — delete integration
 * POST   /api/v1/integrations/test-connection  — test connection (before saving)
 * POST   /api/v1/integrations/{id}/test        — test a saved integration's connection
 *
 * Part 2 will add:
 * POST   /api/v1/integrations/{id}/push        — push invoices
 * GET    /api/v1/integrations/{id}/logs        — push history
 */
@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Slf4j
public class IntegrationController {

    private final IntegrationService integrationService;

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<IntegrationResponse>> list(@AuthenticationPrincipal User currentUser) {
        List<IntegrationResponse> result = integrationService
            .listForOrg(currentUser.getOrganization())
            .stream()
            .map(IntegrationResponse::from)
            .toList();
        return ResponseEntity.ok(result);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> create(
        @Valid @RequestBody IntegrationRequest req,
        @AuthenticationPrincipal User currentUser
    ) {
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Password is required when creating an integration"));
        }

        IntegrationConfig cfg = IntegrationConfig.builder()
            .organization(currentUser.getOrganization())
            .createdBy(currentUser)
            .label(req.getLabel())
            .dbType(req.getDbType())
            .host(req.getHost())
            .port(req.getPort())
            .databaseName(req.getDatabaseName())
            .username(req.getUsername())
            .encryptedPassword(integrationService.encryptPassword(req.getPassword()))
            .useSsl(req.isUseSsl())
            .targetTable(blankToNull(req.getTargetTable()))
            .lineItemsStrategy(req.getLineItemsStrategy())
            .pushMode(req.getPushMode())
            .build();

        IntegrationConfig saved = integrationService.save(cfg);
        log.info("Integration '{}' ({}) created by {}", saved.getLabel(), saved.getDbType(),
            currentUser.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(IntegrationResponse.from(saved));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
        @PathVariable UUID id,
        @Valid @RequestBody IntegrationRequest req,
        @AuthenticationPrincipal User currentUser
    ) {
        return integrationService.findByIdAndOrg(id, currentUser.getOrganization())
            .map(cfg -> {
                cfg.setLabel(req.getLabel());
                cfg.setDbType(req.getDbType());
                cfg.setHost(req.getHost());
                cfg.setPort(req.getPort());
                cfg.setDatabaseName(req.getDatabaseName());
                cfg.setUsername(req.getUsername());
                cfg.setUseSsl(req.isUseSsl());
                cfg.setTargetTable(blankToNull(req.getTargetTable()));
                cfg.setLineItemsStrategy(req.getLineItemsStrategy());
                cfg.setPushMode(req.getPushMode());

                // Only update password if a new one is provided
                if (req.getPassword() != null && !req.getPassword().isBlank()) {
                    cfg.setEncryptedPassword(integrationService.encryptPassword(req.getPassword()));
                }

                IntegrationConfig saved = integrationService.save(cfg);
                return ResponseEntity.ok((Object) IntegrationResponse.from(saved));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        boolean deleted = integrationService.delete(id, currentUser.getOrganization());
        return deleted
            ? ResponseEntity.ok(Map.of("message", "Integration deleted"))
            : ResponseEntity.notFound().build();
    }

    // ── Test connection (before saving — sends raw password in body) ──────────

    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(
        @Valid @RequestBody IntegrationRequest req
    ) {
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Password is required to test the connection"
            ));
        }

        IntegrationService.ConnectionTestResult result = integrationService.testConnection(
            req.getDbType(), req.getHost(), req.getPort(),
            req.getDatabaseName(), req.getUsername(),
            req.getPassword(), req.isUseSsl()
        );

        Map<String, Object> body = result.latencyMs() != null
            ? Map.of("success", result.success(), "message", result.message(),
                     "latencyMs", result.latencyMs())
            : Map.of("success", result.success(), "message", result.message());

        return result.success()
            ? ResponseEntity.ok(body)
            : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    // ── Test a saved integration's connection ─────────────────────────────────

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testSaved(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        return integrationService.findByIdAndOrg(id, currentUser.getOrganization())
            .map(cfg -> {
                IntegrationService.ConnectionTestResult result =
                    integrationService.testSavedConnection(cfg);
                Map<String, Object> body = result.latencyMs() != null
                    ? Map.of("success", result.success(), "message", result.message(),
                             "latencyMs", result.latencyMs())
                    : Map.of("success", result.success(), "message", result.message());
                return result.success()
                    ? ResponseEntity.ok(body)
                    : ResponseEntity.<Map<String, Object>>status(HttpStatus.BAD_GATEWAY).body(body);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Push invoices ──────────────────────────────────────────────────────────

    @PostMapping("/{id}/push")
    public ResponseEntity<?> push(
        @PathVariable UUID id,
        @RequestBody PushRequest req,
        @AuthenticationPrincipal User currentUser
    ) {
        return integrationService.findByIdAndOrg(id, currentUser.getOrganization())
            .map(cfg -> {
                IntegrationService.PushResult result = integrationService.pushInvoices(
                    cfg,
                    req.getInvoiceIds() != null ? req.getInvoiceIds() : List.of(),
                    Boolean.TRUE.equals(req.getPushAll())
                );
                return ResponseEntity.ok((Object) Map.of(
                    "status",         result.status(),
                    "totalAttempted", result.totalAttempted(),
                    "totalSucceeded", result.totalSucceeded(),
                    "totalFailed",    result.totalFailed(),
                    "failures",       result.failures()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Push logs ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/logs")
    public ResponseEntity<?> logs(
        @PathVariable UUID id,
        @AuthenticationPrincipal User currentUser
    ) {
        return integrationService.findByIdAndOrg(id, currentUser.getOrganization())
            .map(cfg -> {
                List<IntegrationPushLog> logs = integrationService.getLogsForIntegration(cfg);
                List<Map<String, Object>> result = logs.stream().map(l -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id",             l.getId());
                    m.put("status",         l.getStatus());
                    m.put("totalAttempted", l.getTotalAttempted());
                    m.put("totalSucceeded", l.getTotalSucceeded());
                    m.put("totalFailed",    l.getTotalFailed());
                    m.put("pushedAt",       l.getPushedAt());
                    return m;
                }).toList();
                return ResponseEntity.ok((Object) result);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Request body for push ─────────────────────────────────────────────────

    @Getter @Setter
    static class PushRequest {
        private List<UUID> invoiceIds;
        private Boolean pushAll;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
