package com.example.securedocs.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {
    @Id
    private UUID id;

    @Column(name = "s3_key", nullable = false, unique = true, length = 512)
    private String s3Key;

    @Column(name = "kms_key_id", nullable = false, length = 512)
    private String kmsKeyId;

    @Column(name = "encrypted_data_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedDataKey;

    @Column(name = "encrypted_metadata", nullable = false, columnDefinition = "TEXT")
    private String encryptedMetadata;

    @Column(name = "uploaded_by", nullable = false, length = 80)
    private String uploadedBy;

    @Column(name = "uploaded_date", nullable = false)
    private Instant uploadedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status;
}
