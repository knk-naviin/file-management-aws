package com.example.securedocs.gateway;

import java.util.Base64;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig {
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.public-key-base64}") String encodedPublicKey) {
        try {
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encodedPublicKey)));
            return NimbusReactiveJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("JWT public key is invalid", exception);
        }
    }

    @Bean
    SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(roles.convert(jwt)));
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/auth/login", "/auth/register", "/auth/refresh", "/actuator/health/**").permitAll()
                        .pathMatchers("/auth/**", "/documents/**", "/users/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtDecoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}
