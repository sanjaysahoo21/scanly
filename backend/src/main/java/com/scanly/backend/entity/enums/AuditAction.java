package com.scanly.backend.entity.enums;

/**
 * Actions recorded in audit logs.
 */
public enum AuditAction {
    UPLOAD,
    PROCESS_COMPLETE,
    PROCESS_FAILED,
    EDIT,
    APPROVE,
    REJECT,
    REPROCESS
}
