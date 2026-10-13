package com.example.securedocs.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Service
public class S3StorageService {
    private final S3Client s3;
    private final String bucket;
    private final String kmsKeyId;

    public S3StorageService(S3Client s3, @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.kms.key-id}") String kmsKeyId) {
        this.s3 = s3;
        this.bucket = bucket;
        this.kmsKeyId = kmsKeyId;
    }

    public void upload(String key, byte[] ciphertext, UUID documentId, String owner, String encryptedDataKey) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType("application/octet-stream")
                .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                .ssekmsKeyId(kmsKeyId)
                .metadata(Map.of("encryption", "AES-256-GCM", "document-id", documentId.toString(),
                        "owner", owner, "encrypted-data-key", encryptedDataKey))
                .build(), RequestBody.fromBytes(ciphertext));
    }

    public byte[] download(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
