package com.platform.ecommerce.user.dto;

import com.platform.ecommerce.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
            @NotBlank String fullName,
            User.Role role
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String email,
            User.Role role
    ) {}
}
