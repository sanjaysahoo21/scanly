package com.scanly.backend.service;

import com.scanly.backend.entity.AuditLog;
import com.scanly.backend.entity.Document;
import com.scanly.backend.entity.Invoice;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.AuditAction;
import com.scanly.backend.entity.enums.EntityType;
import com.scanly.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public void recordDocument(Document document, User user, AuditAction action, String field, String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
            .document(document).user(user).entityType(EntityType.DOCUMENT).entityId(document.getId())
            .fieldName(field).oldValue(oldValue).newValue(newValue).action(action).build());
    }

    public void recordInvoice(Invoice invoice, User user, AuditAction action, String field, String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
            .document(invoice.getDocument()).invoice(invoice).user(user).entityType(EntityType.INVOICE).entityId(invoice.getId())
            .fieldName(field).oldValue(oldValue).newValue(newValue).action(action).build());
    }
}
