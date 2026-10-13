package com.example.securedocs.document.repository;

import com.example.securedocs.document.model.Document;
import com.example.securedocs.document.model.DocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByUploadedByAndStatusOrderByUploadedDateDesc(String uploadedBy, DocumentStatus status);
    List<Document> findAllByStatusOrderByUploadedDateDesc(DocumentStatus status);
    Optional<Document> findByIdAndStatus(UUID id, DocumentStatus status);
}
