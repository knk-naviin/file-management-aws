package com.example.securedocs.auth.token;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteByExpiresAtBefore(Instant instant);
}
