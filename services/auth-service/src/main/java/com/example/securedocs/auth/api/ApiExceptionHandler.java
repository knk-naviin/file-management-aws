package com.example.securedocs.auth.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Request validation failed");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage())).toList());
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A conflicting record already exists");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail authentication(AuthenticationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
