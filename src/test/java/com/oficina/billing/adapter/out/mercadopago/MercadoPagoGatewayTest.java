package com.oficina.billing.adapter.out.mercadopago;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento.*;
import com.oficina.billing.config.BillingProperties;
import com.oficina.billing.domain.Dinheiro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoGatewayTest {
    WireMockServer mp;
    MercadoPagoGateway gateway;

    @BeforeEach void iniciar() {
        mp = new WireMockServer(wireMockConfig().dynamicPort());
        mp.start();
        gateway = new MercadoPagoGateway(RestClient.builder(), new BillingProperties.MercadoPago(mp.baseUrl(), "token-teste",
                "segredo", "https://oficina.example/billing/webhooks/mercadopago", true, Duration.ofMinutes(5)));
    }

    @AfterEach void parar() { mp.stop(); }

    @Test void criaPreferenciaComReferenciaExternaEUsaUrlSandbox() {
        UUID pagamento = UUID.randomUUID();
        mp.stubFor(post("/checkout/preferences").willReturn(okJson("""
                {"id":"pref-1","init_point":"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1",
                 "sandbox_init_point":"https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-1"}""")));
        var c = gateway.criarCobranca(new SolicitacaoCobranca(pagamento, UUID.randomUUID(), 42, Dinheiro.de("151.00"),
                Instant.parse("2026-09-15T12:30:00Z")));
        assertThat(c.preferenceId()).isEqualTo("pref-1");
        assertThat(c.checkoutUrl()).startsWith("https://sandbox.mercadopago.com.br/");
        mp.verify(postRequestedFor(urlEqualTo("/checkout/preferences"))
                .withHeader("Authorization", equalTo("Bearer token-teste"))
                .withRequestBody(matchingJsonPath("$.external_reference", equalTo(pagamento.toString())))
                .withRequestBody(matchingJsonPath("$.items[0].unit_price", equalTo("151.0")))
                .withRequestBody(matchingJsonPath("$.items[0].currency_id", equalTo("BRL")))
                .withRequestBody(matchingJsonPath("$.notification_url", equalTo("https://oficina.example/billing/webhooks/mercadopago")))
                .withRequestBody(matchingJsonPath("$.expiration_date_to", equalTo("2026-09-15T09:30:00.000-03:00"))));
    }

    @Test void consultaPagamentoMapeiaStatus() {
        UUID pagamento = UUID.randomUUID();
        mp.stubFor(get("/v1/payments/987").willReturn(okJson(
                "{\"id\":987,\"status\":\"approved\",\"transaction_amount\":151.00,\"currency_id\":\"BRL\",\"external_reference\":\"" + pagamento + "\"}")));
        mp.stubFor(get("/v1/payments/988").willReturn(okJson(
                "{\"id\":988,\"status\":\"rejected\",\"external_reference\":\"" + pagamento + "\"}")));
        mp.stubFor(get("/v1/payments/989").willReturn(okJson(
                "{\"id\":989,\"status\":\"in_process\",\"external_reference\":\"nao-uuid\"}")));
        assertThat(gateway.consultarPagamento("987")).isEqualTo(new SituacaoPagamento("987", pagamento, StatusExterno.APROVADO, new java.math.BigDecimal("151.00"), "BRL"));
        assertThat(gateway.consultarPagamento("988").status()).isEqualTo(StatusExterno.RECUSADO);
        assertThat(gateway.consultarPagamento("989")).isEqualTo(new SituacaoPagamento("989", null, StatusExterno.PENDENTE, null, null));
    }

    @Test void estornoEnviaChaveDeIdempotencia() {
        UUID chave = UUID.randomUUID();
        mp.stubFor(post("/v1/payments/987/refunds").willReturn(aResponse().withStatus(201).withBody("{\"id\":1}")));
        gateway.estornar("987", chave);
        mp.verify(postRequestedFor(urlEqualTo("/v1/payments/987/refunds")).withHeader("X-Idempotency-Key", equalTo(chave.toString())));
    }

    @Test void errosDoProvedorViramGatewayIndisponivel() {
        mp.stubFor(post("/checkout/preferences").willReturn(serverError()));
        mp.stubFor(get("/v1/payments/1").willReturn(aResponse().withStatus(404)));
        mp.stubFor(post("/v1/payments/1/refunds").willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> gateway.criarCobranca(new SolicitacaoCobranca(UUID.randomUUID(), UUID.randomUUID(), 1,
                Dinheiro.de("1"), Instant.parse("2026-09-15T12:30:00Z")))).isInstanceOf(GatewayIndisponivelException.class);
        assertThatThrownBy(() -> gateway.consultarPagamento("1")).isInstanceOf(GatewayIndisponivelException.class);
        assertThatThrownBy(() -> gateway.estornar("1", UUID.randomUUID())).isInstanceOf(GatewayIndisponivelException.class);
    }

    @Test void urlDeCheckoutNaoHttpsERecusada() {
        mp.stubFor(post("/checkout/preferences").willReturn(okJson("{\"id\":\"p\",\"sandbox_init_point\":\"http://x\"}")));
        assertThatThrownBy(() -> gateway.criarCobranca(new SolicitacaoCobranca(UUID.randomUUID(), UUID.randomUUID(), 1,
                Dinheiro.de("1"), Instant.parse("2026-09-15T12:30:00Z")))).isInstanceOf(GatewayIndisponivelException.class);
    }
}
