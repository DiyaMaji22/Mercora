package com.platform.ecommerce.user.service;

import com.platform.ecommerce.user.config.JwtTokenProvider;
import com.platform.ecommerce.user.dto.AuthDtos.AuthResponse;
import com.platform.ecommerce.user.dto.AuthDtos.LoginRequest;
import com.platform.ecommerce.user.dto.AuthDtos.RegisterRequest;
import com.platform.ecommerce.user.entity.User;
import com.platform.ecommerce.user.exception.DuplicateEmailException;
import com.platform.ecommerce.user.exception.InvalidCredentialsException;
import com.platform.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role() != null ? request.role() : User.Role.CUSTOMER)
                .active(true)
                .build();

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(user);
        return new AuthResponse(
                token, "Bearer", jwtTokenProvider.getExpirationSeconds(),
                user.getEmail(), user.getRole());
    }
}
