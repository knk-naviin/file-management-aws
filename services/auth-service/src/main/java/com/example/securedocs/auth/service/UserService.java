package com.example.securedocs.auth.service;

import com.example.securedocs.auth.api.AuthDtos.RegisterRequest;
import com.example.securedocs.auth.api.AuthDtos.UserResponse;
import com.example.securedocs.auth.user.Role;
import com.example.securedocs.auth.user.User;
import com.example.securedocs.auth.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username()) || users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already registered");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email().toLowerCase(java.util.Locale.ROOT));
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        return toResponse(users.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse profile(String username) {
        return users.findByUsername(username).map(UserService::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
