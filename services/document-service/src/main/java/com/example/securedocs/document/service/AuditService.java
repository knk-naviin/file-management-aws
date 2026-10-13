package com.example.securedocs.document.service;

import com.example.securedocs.document.model.AuditRecord;
import com.example.securedocs.document.repository.AuditRepository;
import com.example.securedocs.document.security.KmsEncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditRepository audits;
    private final KmsEncryptionService encryption;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository audits, KmsEncryptionService encryption, ObjectMapper objectMapper) {
        this.audits = audits;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String action, String userId, UUID documentId) {
        UUID auditId = UUID.randomUUID();
        AuditRecord record = new AuditRecord();
        record.setId(auditId);
        record.setAction(action);
        record.setUserId(userId);
        record.setTimestamp(Instant.now());
        try {
            String details = objectMapper.writeValueAsString(Map.of("documentId", documentId.toString()));
            record.setEncryptedDetails(encryption.encryptMetadata(details, auditId, userId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit details", exception);
        }
        audits.save(record);
    }
}
