package com.example.securedocs.auth.api;

import com.example.securedocs.auth.api.AuthDtos.UserResponse;
import com.example.securedocs.auth.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth", "/users"})
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"/profile", "/me"})
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public UserResponse profile(@AuthenticationPrincipal String username) {
        return userService.profile(username);
    }
}
