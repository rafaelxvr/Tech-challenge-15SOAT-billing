package com.oficina.billing.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates os-service staff access tokens. billing has no user table, so the signed `roles` claim is the
 * authority (os-service re-checks the database role; tokens are short-lived).
 */
@Component
public class StaffTokenVerifier {
    private static final Set<String> ROLES = Set.of("ADMIN", "MECANICO");
    private final JwtProperties properties;
    private final SecretKey key;
    private final Clock clock;

    public StaffTokenVerifier(JwtProperties properties, Clock clock) {
        var staff = properties.staff();
        if (properties.secret() == null || properties.secret().isBlank() || staff == null
                || staff.issuer() == null || staff.issuer().isBlank() || staff.audience() == null
                || staff.audience().isBlank() || staff.keyId() == null || staff.keyId().isBlank()) {
            throw new IllegalArgumentException("Staff JWT trust must be configured");
        }
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public Authentication autenticar(String token) {
        var staff = properties.staff();
        Claims claims = TokenVerification.verify(token, "HS256", Map.of(staff.keyId(), key), staff.issuer(),
                staff.audience(), "staff", "access", clock);
        if (claims.containsKey("scopes") || claims.containsKey("identity_version")) throw TokenVerification.invalid();
        Object roles = claims.get("roles");
        if (!(roles instanceof List<?> lista)) throw TokenVerification.invalid();
        var autoridades = lista.stream().filter(String.class::isInstance).map(String.class::cast).filter(ROLES::contains)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        if (autoridades.isEmpty()) throw TokenVerification.invalid();
        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, autoridades);
    }
}
