package com.oficina.billing.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Signs tokens exactly like os-service JwtService (HS256 over the UTF-8 bytes of the configured secret). */
public final class TokensTeste {
    public static final String SEGREDO = "dGVzdGUtc2VncmVkby1kZS0zMi1ieXRlcy1wYXJhLWhzMjU2LW9rIQ==";

    private TokensTeste() {}

    public static String staff(String role) { return staff(role, Map.of()); }

    public static String staff(String role, Map<String, Object> extras) {
        Instant agora = Instant.now();
        return Jwts.builder().claims(extras).header().keyId("staff-test").and()
                .issuer("oficina-test").audience().add("oficina-staff-test").and()
                .subject("staff@example.invalid").issuedAt(Date.from(agora.minusSeconds(5)))
                .expiration(Date.from(agora.plus(Duration.ofMinutes(10))))
                .claim("principal_type", "staff").claim("token_use", "access").claim("roles", List.of(role))
                .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    public static String cliente() {
        Instant agora = Instant.now();
        return Jwts.builder().header().keyId("staff-test").and()
                .issuer("oficina-test").audience().add("oficina-staff-test").and()
                .subject("cliente").issuedAt(Date.from(agora.minusSeconds(5))).expiration(Date.from(agora.plusSeconds(600)))
                .claim("principal_type", "customer").claim("token_use", "access").claim("roles", List.of("ADMIN"))
                .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
}
