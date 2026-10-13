package com.example.securedocs.document.api;

import com.example.securedocs.document.api.DocumentDtos.DocumentResponse;
import com.example.securedocs.document.client.AuthProfileClient;
import com.example.securedocs.document.client.AuthProfileClient.AuthUserProfile;
import com.example.securedocs.document.service.DocumentService;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/documents")
public class DocumentController {
    private final DocumentService documentService;
    private final AuthProfileClient authProfileClient;

    public DocumentController(DocumentService documentService, AuthProfileClient authProfileClient) {
        this.documentService = documentService;
        this.authProfileClient = authProfileClient;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestParam("file") @NotNull MultipartFile file,
            @AuthenticationPrincipal Jwt jwt, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization)
            throws java.io.IOException {
        AuthUserProfile user = currentUser(jwt, authorization);
        return documentService.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                user.username());
    }

    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthUserProfile user = currentUser(jwt, authorization);
        return documentService.list(user.username(), isAdmin(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthUserProfile user = currentUser(jwt, authorization);
        DocumentService.DownloadedDocument document =
                documentService.download(id, user.username(), isAdmin(user));
        MediaType mediaType = MediaType.parseMediaType(document.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(document.contents());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthUserProfile user = currentUser(jwt, authorization);
        documentService.delete(id, user.username(), isAdmin(user));
    }

    private AuthUserProfile currentUser(Jwt jwt, String authorization) {
        AuthUserProfile user = authProfileClient.profile(authorization);
        if (!user.username().equals(jwt.getSubject())) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated identity mismatch");
        }
        return user;
    }

    private static boolean isAdmin(AuthUserProfile user) {
        return user.role().equals("ADMIN");
    }
}
