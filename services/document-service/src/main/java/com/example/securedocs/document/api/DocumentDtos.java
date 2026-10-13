package com.example.securedocs.document.api;

import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {
    private DocumentDtos() { }

    public record DocumentResponse(UUID id, String fileName, String contentType, long size,
            String uploadedBy, Instant uploadedDate, String status) { }
}
