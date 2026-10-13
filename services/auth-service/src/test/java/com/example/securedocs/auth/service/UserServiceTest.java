package com.example.securedocs.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.securedocs.auth.api.AuthDtos.RegisterRequest;
import com.example.securedocs.auth.user.Role;
import com.example.securedocs.auth.user.User;
import com.example.securedocs.auth.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class UserServiceTest {
    private final UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
    private final PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    private final UserService service = new UserService(users, encoder);

    @Test
    void registrationHashesPasswordAndAssignsLeastPrivilegeRole() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(encoder.encode("strong-password")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });

        var result = service.register(new RegisterRequest("alice", "alice@example.com", "strong-password"));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.role()).isEqualTo("USER");
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("bcrypt-hash");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void duplicateIdentityIsRejectedBeforeSaving() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice", "a@example.com", "strong-password")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(users, never()).save(any());
    }
}
