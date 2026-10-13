package com.example.securedocs.auth.api;

import com.example.securedocs.auth.api.AuthDtos.AuthResponse;
import com.example.securedocs.auth.api.AuthDtos.LoginRequest;
import com.example.securedocs.auth.api.AuthDtos.LogoutRequest;
import com.example.securedocs.auth.api.AuthDtos.RefreshRequest;
import com.example.securedocs.auth.api.AuthDtos.RegisterRequest;
import com.example.securedocs.auth.api.AuthDtos.UserResponse;
import com.example.securedocs.auth.service.AuthService;
import com.example.securedocs.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal String username, @Valid @RequestBody LogoutRequest request) {
        authService.logout(username, request.refreshToken());
    }
}
