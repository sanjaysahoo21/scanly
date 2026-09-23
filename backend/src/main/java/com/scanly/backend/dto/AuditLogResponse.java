package com.scanly.backend.dto;

import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.entity.enums.EntityType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe API projection for AuditLog.
 * Surfaces document filename and user name without exposing full JPA entities.
 */
@Getter
@Builder
public class AuditLogResponse {
    private UUID id;
    private UUID entityId;
    private EntityType entityType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private AuditAction action;
    private Instant createdAt;

    // Flattened user info
    private String userFullName;
    private String userEmail;

    // Flattened document info
    private UUID documentId;
    private String documentFileName;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
            .id(log.getId())
            .entityId(log.getEntityId())
            .entityType(log.getEntityType())
            .fieldName(log.getFieldName())
            .oldValue(log.getOldValue())
            .newValue(log.getNewValue())
            .action(log.getAction())
            .createdAt(log.getCreatedAt())
            .userFullName(log.getUser() != null ? log.getUser().getFullName() : null)
            .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
            .documentId(log.getDocument() != null ? log.getDocument().getId() : null)
            .documentFileName(log.getDocument() != null ? log.getDocument().getFileName() : null)
            .build();
    }
}
