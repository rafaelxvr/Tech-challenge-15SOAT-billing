package com.oficina.billing.security;

import com.oficina.billing.support.TokensTeste;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Control S-2: the staff token is rejected unless every trusted attribute matches. */
class StaffTokenVerifierTest {
    static final Clock RELOGIO = Clock.systemUTC();
    final StaffTokenVerifier verificador = new StaffTokenVerifier(new JwtProperties(TokensTeste.SEGREDO,
            new JwtProperties.Staff("oficina-test", "oficina-staff-test", "staff-test")), RELOGIO);

    static JwtBuilder base() {
        Instant agora = Instant.now();
        return Jwts.builder().header().keyId("staff-test").and().issuer("oficina-test")
                .audience().add("oficina-staff-test").and().subject("staff@example.invalid")
                .issuedAt(Date.from(agora.minusSeconds(5))).expiration(Date.from(agora.plus(Duration.ofMinutes(10))))
                .claim("principal_type", "staff").claim("token_use", "access").claim("roles", List.of("ADMIN"));
    }

    static String assinado(JwtBuilder b) {
        return b.signWith(Keys.hmacShaKeyFor(TokensTeste.SEGREDO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    void rejeita(String token) {
        assertThatThrownBy(() -> verificador.autenticar(token)).isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test void tokenValidoEAceito() {
        assertThat(verificador.autenticar(TokensTeste.staff("ADMIN")).getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test void chaveDiferenteERejeitada() {
        String outroSegredo = "b3V0cm8tc2VncmVkby1kZS0zMi1ieXRlcy1wYXJhLWhzMjU2LW9rISE=";
        rejeita(base().signWith(Keys.hmacShaKeyFor(outroSegredo.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact());
    }

    @Test void algNoneERejeitado() {
        rejeita(base().compact());
    }

    @Test void algoritmoTrocadoERejeitado() {
        rejeita(base().signWith(Keys.hmacShaKeyFor((TokensTeste.SEGREDO + TokensTeste.SEGREDO).getBytes(StandardCharsets.UTF_8)),
                Jwts.SIG.HS512).compact());
    }

    @Test void emissorAudienciaKidEPropositoIncorretosSaoRejeitados() {
        rejeita(assinado(base().issuer("outro")));
        rejeita(assinado(base().audience().add("outra").and()));
        rejeita(assinado(base().header().keyId("outro-kid").and()));
        rejeita(assinado(base().claim("token_use", "refresh")));
        rejeita(assinado(base().claim("principal_type", "customer")));
    }

    @Test void tokenVencidoOuEmitidoNoFuturoERejeitado() {
        Instant agora = Instant.now();
        rejeita(assinado(base().issuedAt(Date.from(agora.minusSeconds(600))).expiration(Date.from(agora.minusSeconds(60)))));
        rejeita(assinado(base().issuedAt(Date.from(agora.plusSeconds(600))).expiration(Date.from(agora.plusSeconds(1200)))));
    }

    @Test void papelDesconhecidoOuMalFormadoERejeitado() {
        rejeita(assinado(base().claim("roles", List.of("CLIENTE"))));
        rejeita(assinado(base().claim("roles", "ADMIN")));
    }

    @Test void configuracaoIncompletaImpedeAInicializacao() {
        assertThatThrownBy(() -> new StaffTokenVerifier(new JwtProperties("", new JwtProperties.Staff("a", "b", "c")), RELOGIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffTokenVerifier(new JwtProperties(TokensTeste.SEGREDO, null), RELOGIO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
