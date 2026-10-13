package com.example.securedocs.document.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.securedocs.document.model.Document;
import com.example.securedocs.document.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.schemas=PUBLIC",
        "spring.flyway.default-schema=PUBLIC",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class DocumentRepositoryIntegrationTest {
    @Autowired
    private DocumentRepository documents;

    @Test
    void persistsEncryptedMetadataAndSupportsOwnerScopedListing() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setS3Key("documents/" + document.getId());
        document.setKmsKeyId("alias/test");
        document.setEncryptedDataKey("wrapped-key");
        document.setEncryptedMetadata("encrypted-metadata");
        document.setUploadedBy("alice");
        document.setUploadedDate(Instant.now());
        document.setStatus(DocumentStatus.ACTIVE);

        documents.saveAndFlush(document);

        assertThat(documents.findAllByUploadedByAndStatusOrderByUploadedDateDesc("alice", DocumentStatus.ACTIVE))
                .singleElement().extracting(Document::getEncryptedMetadata).isEqualTo("encrypted-metadata");
    }
}
