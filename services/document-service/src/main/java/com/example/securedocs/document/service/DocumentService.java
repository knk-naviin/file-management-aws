package com.example.securedocs.document.service;

import com.example.securedocs.document.api.DocumentDtos.DocumentResponse;
import com.example.securedocs.document.model.Document;
import com.example.securedocs.document.model.DocumentStatus;
import com.example.securedocs.document.repository.DocumentRepository;
import com.example.securedocs.document.security.KmsEncryptionService;
import com.example.securedocs.document.storage.S3StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final S3StorageService storage;
    private final KmsEncryptionService encryption;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final String kmsKeyId;
    private final long maxFileBytes;

    public DocumentService(DocumentRepository documents, S3StorageService storage, KmsEncryptionService encryption,
            AuditService auditService, ObjectMapper objectMapper, @Value("${aws.kms.key-id}") String kmsKeyId,
            @Value("${documents.max-file-bytes:52428800}") long maxFileBytes) {
        this.documents = documents;
        this.storage = storage;
        this.encryption = encryption;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.kmsKeyId = kmsKeyId;
        this.maxFileBytes = maxFileBytes;
    }

    @Transactional
    public DocumentResponse upload(String fileName, String contentType, byte[] contents, String username) {
        if (contents == null || contents.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file must not be empty");
        }
        if (contents.length > maxFileBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the configured limit");
        }
        String safeName = sanitizeFileName(fileName);
        String safeType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        UUID id = UUID.randomUUID();
        KmsEncryptionService.EncryptedFile encrypted = encryption.encryptFile(contents, id, username);
        String s3Key = "documents/" + id;
        Document document = new Document();
        document.setId(id);
        document.setS3Key(s3Key);
        document.setKmsKeyId(kmsKeyId);
        document.setEncryptedDataKey(encrypted.encryptedDataKey());
        document.setEncryptedMetadata(encryptMetadata(
                new DocumentMetadata(safeName, safeType, contents.length), id, username));
        document.setUploadedBy(username);
        document.setUploadedDate(Instant.now());
        document.setStatus(DocumentStatus.ACTIVE);
        try {
            storage.upload(s3Key, encrypted.ciphertext(), id, username, encrypted.encryptedDataKey());
            Document saved = documents.save(document);
            auditService.record("UPLOAD", username, id);
            return response(saved);
        } catch (RuntimeException exception) {
            try {
                storage.delete(s3Key);
            } catch (RuntimeException compensationFailure) {
                exception.addSuppressed(compensationFailure);
            }
            throw exception;
        } finally {
            Arrays.fill(encrypted.ciphertext(), (byte) 0);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(String username, boolean admin) {
        List<Document> found = admin
                ? documents.findAllByStatusOrderByUploadedDateDesc(DocumentStatus.ACTIVE)
                : documents.findAllByUploadedByAndStatusOrderByUploadedDateDesc(username, DocumentStatus.ACTIVE);
        return found.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DownloadedDocument download(UUID id, String username, boolean admin) {
        Document document = findAccessible(id, username, admin);
        byte[] ciphertext = storage.download(document.getS3Key());
        byte[] plaintext = encryption.decryptFile(ciphertext, document.getEncryptedDataKey(), document.getId(),
                document.getUploadedBy());
        DocumentMetadata metadata = decryptMetadata(document);
        return new DownloadedDocument(plaintext, metadata.fileName(), metadata.contentType());
    }

    @Transactional
    public void delete(UUID id, String username, boolean admin) {
        Document document = findAccessible(id, username, admin);
        storage.delete(document.getS3Key());
        document.setStatus(DocumentStatus.DELETED);
        documents.save(document);
        auditService.record("DELETE", username, id);
    }

    private Document findAccessible(UUID id, String username, boolean admin) {
        Document document = documents.findByIdAndStatus(id, DocumentStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!admin && !document.getUploadedBy().equals(username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }

    private DocumentResponse response(Document document) {
        DocumentMetadata metadata = decryptMetadata(document);
        return new DocumentResponse(document.getId(), metadata.fileName(), metadata.contentType(), metadata.size(),
                document.getUploadedBy(), document.getUploadedDate(), document.getStatus().name());
    }

    private String encryptMetadata(DocumentMetadata metadata, UUID id, String username) {
        try {
            return encryption.encryptMetadata(objectMapper.writeValueAsString(metadata), id, username);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize document metadata", exception);
        }
    }

    private DocumentMetadata decryptMetadata(Document document) {
        try {
            String plaintext = encryption.decryptMetadata(document.getEncryptedMetadata(), document.getId(), document.getUploadedBy());
            return objectMapper.readValue(plaintext, DocumentMetadata.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read encrypted document metadata", exception);
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name is required");
        }
        String normalized = fileName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.length() > 255 || name.equals(".") || name.equals("..")
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        return name;
    }

    public record DownloadedDocument(byte[] contents, String fileName, String contentType) { }
    private record DocumentMetadata(String fileName, String contentType, long size) { }
}
