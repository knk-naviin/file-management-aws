package com.example.securedocs.document.repository;

import com.example.securedocs.document.model.AuditRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditRecord, UUID> {
}
