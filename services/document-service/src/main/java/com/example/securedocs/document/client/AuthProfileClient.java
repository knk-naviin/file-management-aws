package com.example.securedocs.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", url = "${services.auth.url}")
public interface AuthProfileClient {
    @GetMapping("/auth/profile")
    AuthUserProfile profile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    record AuthUserProfile(Long id, String username, String email, String role) { }
}
