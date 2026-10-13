package com.example.securedocs.document.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;

@Service
public class KmsEncryptionService {
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private final KmsClient kms;
    private final String keyId;
    private final SecureRandom secureRandom = new SecureRandom();

    public KmsEncryptionService(KmsClient kms, @Value("${aws.kms.key-id}") String keyId) {
        this.kms = kms;
        this.keyId = keyId;
    }

    public EncryptedFile encryptFile(byte[] plaintext, UUID documentId, String owner) {
        var generated = kms.generateDataKey(GenerateDataKeyRequest.builder()
                .keyId(keyId).keySpec(DataKeySpec.AES_256).encryptionContext(context(documentId, owner)).build());
        byte[] dataKey = generated.plaintext().asByteArray();
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext);
            ByteBuffer payload = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted);
            return new EncryptedFile(payload.array(), Base64.getEncoder().encodeToString(generated.ciphertextBlob().asByteArray()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt document contents", exception);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    public byte[] decryptFile(byte[] ciphertext, String encryptedDataKey, UUID documentId, String owner) {
        byte[] dataKey = kms.decrypt(DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(encryptedDataKey)))
                .encryptionContext(context(documentId, owner)).build()).plaintext().asByteArray();
        try {
            if (ciphertext.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Encrypted document payload is incomplete");
            }
            ByteBuffer payload = ByteBuffer.wrap(ciphertext);
            byte[] nonce = new byte[NONCE_LENGTH];
            payload.get(nonce);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt document contents", exception);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    public String encryptMetadata(String plaintext, UUID recordId, String owner) {
        var result = kms.encrypt(EncryptRequest.builder()
                .keyId(keyId).plaintext(SdkBytes.fromString(plaintext, StandardCharsets.UTF_8))
                .encryptionContext(context(recordId, owner)).build());
        return Base64.getEncoder().encodeToString(result.ciphertextBlob().asByteArray());
    }

    public String decryptMetadata(String ciphertext, UUID recordId, String owner) {
        var result = kms.decrypt(DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(ciphertext)))
                .encryptionContext(context(recordId, owner)).build());
        return result.plaintext().asString(StandardCharsets.UTF_8);
    }

    private static Map<String, String> context(UUID id, String owner) {
        return Map.of("recordId", id.toString(), "owner", owner);
    }

    public record EncryptedFile(byte[] ciphertext, String encryptedDataKey) { }
}
