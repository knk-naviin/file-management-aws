package com.example.securedocs.auth.service;

import com.example.securedocs.auth.api.AuthDtos.AuthResponse;
import com.example.securedocs.auth.token.RefreshToken;
import com.example.securedocs.auth.token.RefreshTokenRepository;
import com.example.securedocs.auth.user.User;
import com.example.securedocs.auth.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.securedocs.auth.security.JwtService;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final long refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AuthenticationManager authenticationManager, UserRepository users,
            RefreshTokenRepository refreshTokens, JwtService jwtService,
            @Value("${security.jwt.refresh-token-days:30}") long refreshTokenDays) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public AuthResponse login(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String presentedToken) {
        String hash = hashToken(presentedToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!stored.getExpiresAt().isAfter(Instant.now())) {
            refreshTokens.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }
        User user = stored.getUser();
        refreshTokens.delete(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String username, String presentedToken) {
        refreshTokens.findByTokenHash(hashToken(presentedToken)).ifPresent(stored -> {
            if (!stored.getUser().getUsername().equals(username)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Refresh token belongs to another user");
            }
            refreshTokens.delete(stored);
        });
    }

    private AuthResponse issueTokens(User user) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(hashToken(refreshToken), user,
                Instant.now().plusSeconds(Math.multiplyExact(refreshTokenDays, 86_400L))));
        return new AuthResponse(jwtService.generateAccessToken(user), "Bearer",
                jwtService.getAccessTokenSeconds(), refreshToken);
    }

    private static String hashToken(String token) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
