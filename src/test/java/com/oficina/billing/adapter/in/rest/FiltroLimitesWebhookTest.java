package com.oficina.billing.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Control S-4: the webhook rejects oversized bodies and floods before doing any signature or payload work. */
class FiltroLimitesWebhookTest {
    static final Instant T0 = Instant.parse("2026-10-22T12:00:00Z");

    static MockHttpServletRequest webhook(int tamanho) {
        var request = new MockHttpServletRequest("POST", "/billing/webhooks/mercadopago");
        request.setContextPath("/billing");
        request.setContent(new byte[tamanho]);
        return request;
    }

    static int status(FiltroLimitesWebhook filtro, MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test void corpoMaiorQueOLimiteRetorna413() throws Exception {
        var filtro = new FiltroLimitesWebhook(100, new LimitadorTaxa(10, Duration.ofMinutes(1), Clock.fixed(T0, ZoneOffset.UTC)));
        assertThat(status(filtro, webhook(100))).isEqualTo(200);
        assertThat(status(filtro, webhook(101))).isEqualTo(413);
    }

    @Test void excessoDeRequisicoesNaJanelaRetorna429EReiniciaNaProximaJanela() throws Exception {
        var relogio = new RelogioMutavel(T0);
        var filtro = new FiltroLimitesWebhook(100, new LimitadorTaxa(2, Duration.ofMinutes(1), relogio));
        assertThat(status(filtro, webhook(1))).isEqualTo(200);
        assertThat(status(filtro, webhook(1))).isEqualTo(200);
        assertThat(status(filtro, webhook(1))).isEqualTo(429);
        relogio.avancar(Duration.ofMinutes(1));
        assertThat(status(filtro, webhook(1))).isEqualTo(200);
    }

    @Test void outrosCaminhosNaoSaoLimitados() throws Exception {
        var filtro = new FiltroLimitesWebhook(1, new LimitadorTaxa(0, Duration.ofMinutes(1), Clock.fixed(T0, ZoneOffset.UTC)));
        var request = new MockHttpServletRequest("GET", "/billing/actuator/health");
        request.setContextPath("/billing");
        assertThat(status(filtro, request)).isEqualTo(200);
    }

    static final class RelogioMutavel extends Clock {
        private Instant agora;

        RelogioMutavel(Instant inicio) { this.agora = inicio; }

        void avancar(Duration d) { agora = agora.plus(d); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return agora; }
    }
}
