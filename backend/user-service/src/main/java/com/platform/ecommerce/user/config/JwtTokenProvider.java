package com.platform.ecommerce.user.config;

import com.platform.ecommerce.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-seconds:3600}")
    private long expirationSeconds;

    public String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
