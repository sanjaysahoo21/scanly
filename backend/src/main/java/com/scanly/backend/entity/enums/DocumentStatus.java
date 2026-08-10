package com.scanly.backend.entity.enums;

/**
 * Processing status of an uploaded document.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    NEEDS_REVIEW
}
