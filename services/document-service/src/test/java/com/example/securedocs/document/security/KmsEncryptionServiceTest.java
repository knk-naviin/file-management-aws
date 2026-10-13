package com.example.securedocs.document.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

class KmsEncryptionServiceTest {
    private final KmsClient kms = org.mockito.Mockito.mock(KmsClient.class);
    private final byte[] dataKey = new byte[32];
    private final KmsEncryptionService service = new KmsEncryptionService(kms, "alias/test");

    @Test
    void encryptedFileRoundTripsUsingAuthenticatedOwnerContext() {
        java.util.Arrays.fill(dataKey, (byte) 7);
        when(kms.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(GenerateDataKeyResponse.builder()
                .plaintext(SdkBytes.fromByteArray(dataKey))
                .ciphertextBlob(SdkBytes.fromUtf8String("wrapped-key"))
                .build());
        when(kms.decrypt(any(DecryptRequest.class))).thenReturn(DecryptResponse.builder()
                .plaintext(SdkBytes.fromByteArray(dataKey)).build());
        UUID id = UUID.randomUUID();
        byte[] source = "private document contents".getBytes(StandardCharsets.UTF_8);

        var encrypted = service.encryptFile(source, id, "alice");
        byte[] result = service.decryptFile(encrypted.ciphertext(), encrypted.encryptedDataKey(), id, "alice");

        assertThat(result).isEqualTo(source);
        assertThat(encrypted.ciphertext()).isNotEqualTo(source);
        ArgumentCaptor<GenerateDataKeyRequest> request = ArgumentCaptor.forClass(GenerateDataKeyRequest.class);
        verify(kms).generateDataKey(request.capture());
        assertThat(request.getValue().encryptionContext()).containsEntry("recordId", id.toString()).containsEntry("owner", "alice");
        ArgumentCaptor<DecryptRequest> decryptRequest = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(kms).decrypt(decryptRequest.capture());
        assertThat(decryptRequest.getValue().encryptionContext()).containsEntry("recordId", id.toString()).containsEntry("owner", "alice");
    }
}
