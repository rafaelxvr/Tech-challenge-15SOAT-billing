package com.oficina.billing.adapter.out.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffPublicacaoTest {
    static final Instant T = Instant.parse("2026-09-15T12:00:00Z");

    @Test void primeiraFalhaEsperaCincoSegundos() {
        var t = new BackoffPublicacao(bound -> 0).proxima(0, T);
        assertThat(t.numero()).isEqualTo(1);
        assertThat(t.bloqueada()).isFalse();
        assertThat(t.disponivelEm()).isEqualTo(T.plusSeconds(5));
    }

    @Test void tetoDeCincoMinutosEBloqueioNaDecimaSegunda() {
        assertThat(new BackoffPublicacao(bound -> bound - 1).proxima(10, T).disponivelEm()).isEqualTo(T.plusMillis(300_000));
        var ultima = new BackoffPublicacao(bound -> 0).proxima(11, T);
        assertThat(ultima.numero()).isEqualTo(12);
        assertThat(ultima.bloqueada()).isTrue();
    }
}
