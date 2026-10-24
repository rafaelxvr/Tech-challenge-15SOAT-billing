package com.oficina.billing.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificadorGatewayFakeTest {
    static BillingProperties props(String token) {
        return new BillingProperties(null, new BillingProperties.MercadoPago("https://x", token, "s", "https://n", true,
                Duration.ofMinutes(5)), null);
    }

    @Test void fakeSemCredencialRealIniciaNormalmente() {
        assertThat(new VerificadorGatewayFake(props(""))).isNotNull();
        assertThat(new VerificadorGatewayFake(props(null))).isNotNull();
    }

    @Test void fakeComCredencialRealFalhaNaInicializacao() {
        assertThatThrownBy(() -> new VerificadorGatewayFake(props("APP_USR-qualquer")))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("APP_USR");
    }
}
