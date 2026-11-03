package com.oficina.billing.application;

import com.oficina.billing.application.port.GatewayIndisponivelException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TentativasGatewayTest {
    final List<Long> esperas = new ArrayList<>();
    final TentativasGateway tentativas = new TentativasGateway(3, Duration.ofSeconds(1), esperas::add);

    @Test void sucessoNaSegundaTentativa() {
        AtomicInteger chamadas = new AtomicInteger();
        tentativas.executar(() -> { if (chamadas.incrementAndGet() == 1) throw new GatewayIndisponivelException("X"); });
        assertThat(chamadas).hasValue(2);
        assertThat(esperas).containsExactly(1000L);
    }

    @Test void esgotaEPropagaUltimaFalha() {
        assertThatThrownBy(() -> tentativas.executar(() -> { throw new GatewayIndisponivelException("X"); }))
                .isInstanceOf(GatewayIndisponivelException.class);
        assertThat(esperas).containsExactly(1000L, 2000L);
    }

    @Test void outrasFalhasNaoSaoRepetidas() {
        AtomicInteger chamadas = new AtomicInteger();
        assertThatThrownBy(() -> tentativas.executar(() -> { chamadas.incrementAndGet(); throw new IllegalStateException(); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(chamadas).hasValue(1);
    }
}
