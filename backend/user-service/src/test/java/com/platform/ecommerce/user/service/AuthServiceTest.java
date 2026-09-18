package com.platform.ecommerce.user.service;

import com.platform.ecommerce.user.config.JwtTokenProvider;
import com.platform.ecommerce.user.dto.AuthDtos.LoginRequest;
import com.platform.ecommerce.user.dto.AuthDtos.RegisterRequest;
import com.platform.ecommerce.user.entity.User;
import com.platform.ecommerce.user.exception.DuplicateEmailException;
import com.platform.ecommerce.user.exception.InvalidCredentialsException;
import com.platform.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks private AuthService authService;

    @Test
    void register_whenEmailAlreadyExists_throwsDuplicateEmailException() {
        var request = new RegisterRequest("taken@shop.test", "password123", "Someone", User.Role.CUSTOMER);
        when(userRepository.existsByEmail("taken@shop.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withNewEmail_savesUserAndReturnsToken() {
        var request = new RegisterRequest("new@shop.test", "password123", "New User", User.Role.CUSTOMER);
        when(userRepository.existsByEmail("new@shop.test")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("fake-jwt-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);

        var response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("fake-jwt-token");
        assertThat(response.email()).isEqualTo("new@shop.test");
        assertThat(response.role()).isEqualTo(User.Role.CUSTOMER);
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("user@shop.test")
                .passwordHash("hashed").role(User.Role.CUSTOMER).active(true).build();

        when(userRepository.findByEmail("user@shop.test")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@shop.test", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_withInactiveAccount_throwsInvalidCredentials() {
        User existing = User.builder()
                .id(UUID.randomUUID()).email("user@shop.test")
                .passwordHash("hashed").role(User.Role.CUSTOMER).active(false).build();

        when(userRepository.findByEmail("user@shop.test")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@shop.test", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
