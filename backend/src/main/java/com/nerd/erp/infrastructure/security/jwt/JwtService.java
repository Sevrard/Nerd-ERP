package com.nerd.erp.infrastructure.security.jwt;

import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.domain.tenancy.TenantRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(properties.expirationMs());
        return Jwts.builder()
                .subject(user.userId().toString())
                .claim("tenantId", user.tenantId().toString())
                .claim("role", user.role().name())
                .claim("email", user.email())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("tenantId", String.class)),
                TenantRole.valueOf(claims.get("role", String.class)),
                claims.get("email", String.class));
    }
}
