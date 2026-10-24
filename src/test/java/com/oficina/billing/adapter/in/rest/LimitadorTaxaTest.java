package com.oficina.billing.adapter.in.rest;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorTaxaTest {

    @Test void tabelaDeOrigensNuncaExcedeOTeto() {
        var limitador = new LimitadorTaxa(5, Duration.ofMinutes(1), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < LimitadorTaxa.MAXIMO_ORIGENS + 50; i++) {
            limitador.permitir("10.0." + i);
        }
        assertThat(limitador.origensRastreadas()).isEqualTo(LimitadorTaxa.MAXIMO_ORIGENS);
    }
}
