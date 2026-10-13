package com.example.securedocs.auth.security;

import com.example.securedocs.auth.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long accessTokenSeconds;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder,
            @Value("${security.jwt.access-token-seconds:900}") long accessTokenSeconds) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.accessTokenSeconds = accessTokenSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("secure-document-auth")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenSeconds))
                .subject(user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims)).getTokenValue();
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }
}
