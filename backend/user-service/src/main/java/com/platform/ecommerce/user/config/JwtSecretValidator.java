package com.platform.ecommerce.user.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class JwtSecretValidator {

    private static final String INSECURE_DEFAULT = "change-this-dev-only-secret-min-32-bytes-long!";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validate() {
        if (INSECURE_DEFAULT.equals(jwtSecret)) {
            throw new IllegalStateException(
                "JWT_SECRET must be explicitly set to a secure value in production."
            );
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters. Current length: " + jwtSecret.length()
            );
        }
    }
}
