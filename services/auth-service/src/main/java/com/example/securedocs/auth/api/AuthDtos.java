package com.example.securedocs.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password) { }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    public record RefreshRequest(@NotBlank String refreshToken) { }
    public record LogoutRequest(@NotBlank String refreshToken) { }
    public record UserResponse(Long id, String username, String email, String role) { }
    public record AuthResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) { }
}
