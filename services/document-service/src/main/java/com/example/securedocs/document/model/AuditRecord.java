package com.example.securedocs.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_records")
@Getter
@Setter
@NoArgsConstructor
public class AuditRecord {
    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "user_id", nullable = false, length = 80)
    private String userId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "encrypted_details", nullable = false, columnDefinition = "TEXT")
    private String encryptedDetails;
}
