package com.example.securedocs.document.api;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.core.exception.SdkException;
import feign.FeignException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMediaTypeNotSupportedException.class})
    ProblemDetail badRequest(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the configured limit");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(SdkException.class)
    ProblemDetail awsFailure(SdkException exception) {
        log.error("AWS document operation failed", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Document storage is temporarily unavailable");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(FeignException.class)
    ProblemDetail authServiceFailure(FeignException exception) {
        if (exception.status() == HttpStatus.UNAUTHORIZED.value()) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is no longer valid");
        }
        log.error("Auth service request failed", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Authentication service is temporarily unavailable");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail internalFailure(IllegalStateException exception) {
        log.error("Document operation failed", exception);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
