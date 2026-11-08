package com.forgepilot.system.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpireSeconds());
        return Jwts.builder()
                .subject(user.userId())
                .claim("username", user.username())
                .claim("displayName", user.displayName())
                .claim("tenantId", user.tenantId())
                .claim("roles", user.roles())
                .claim("menus", user.menus())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> menus = claims.get("menus", List.class);
        return new AuthUser(
                claims.getSubject(),
                claims.get("username", String.class),
                claims.get("displayName", String.class),
                claims.get("tenantId", String.class),
                roles == null ? List.of() : List.copyOf(roles),
                menus == null ? List.of() : List.copyOf(menus));
    }
}
