package com.platform.ecommerce.user.controller;

import com.platform.ecommerce.user.dto.AuthDtos.AuthResponse;
import com.platform.ecommerce.user.dto.AuthDtos.LoginRequest;
import com.platform.ecommerce.user.dto.AuthDtos.RegisterRequest;
import com.platform.ecommerce.user.repository.UserRepository;
import com.platform.ecommerce.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole()
        ));
    }
}
