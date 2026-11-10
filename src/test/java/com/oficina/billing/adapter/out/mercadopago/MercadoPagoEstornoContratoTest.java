package com.oficina.billing.adapter.out.mercadopago;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.oficina.billing.config.BillingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnóstico local (WireMock, sem chamar o Mercado Pago) do que o estorno realmente envia em
 * POST /v1/payments/{id}/refunds. Os testes só fixam o que a requisição contém; o que o provedor real aceita ou
 * rejeita não é demonstrável aqui e não é inferido.
 */
class MercadoPagoEstornoContratoTest {
    WireMockServer mp;
    MercadoPagoGateway gateway;

    @BeforeEach void iniciar() {
        mp = new WireMockServer(wireMockConfig().dynamicPort());
        mp.start();
        gateway = new MercadoPagoGateway(RestClient.builder(), new BillingProperties.MercadoPago(mp.baseUrl(), "token-teste",
                "segredo", "https://oficina.example/billing/webhooks/mercadopago", true, Duration.ofMinutes(5)));
        mp.stubFor(post("/v1/payments/987/refunds").willReturn(aResponse().withStatus(201).withBody("{\"id\":1}")));
    }

    @AfterEach void parar() { mp.stop(); }

    private LoggedRequest primeiraRequisicao() {
        return mp.findAll(postRequestedFor(urlEqualTo("/v1/payments/987/refunds"))).get(0);
    }

    @Test void estornoAtualEnviaObjetoJsonVazioComHeadersExatos() {
        UUID chave = UUID.randomUUID();
        gateway.estornar("987", chave);
        LoggedRequest req = primeiraRequisicao();
        assertThat(req.getBodyAsString()).isEqualTo("{}");
        assertThat(req.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(req.getHeader("Content-Length")).isEqualTo("2");
        assertThat(req.getHeader("Accept")).isEqualTo("application/json");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer token-teste");
        assertThat(req.getHeader("X-Idempotency-Key")).isEqualTo(chave.toString());
        assertThat(req.getHeader("Transfer-Encoding")).isNull();
        assertThat(req.getQueryParams()).isEmpty();
    }

    @Test void retentativaReusaAChaveDeIdempotencia() {
        UUID chave = UUID.randomUUID();
        gateway.estornar("987", chave);
        gateway.estornar("987", chave);
        List<LoggedRequest> reqs = mp.findAll(postRequestedFor(urlEqualTo("/v1/payments/987/refunds")));
        assertThat(reqs).hasSize(2)
                .allSatisfy(r -> assertThat(r.getHeader("X-Idempotency-Key")).isEqualTo(chave.toString()));
    }

    /**
     * Evidência do comportamento anterior (body(Map.of())): o corpo sai em Transfer-Encoding: chunked, sem
     * Content-Length. O Accept padrão depende do JDK e por isso não é fixado aqui. Se o provedor real rejeita isso
     * não é demonstrável localmente.
     */
    @Test void variacaoAnteriorComMapaEnviavaChunkedSemContentLength() {
        RestClient.builder().baseUrl(mp.baseUrl())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory()).build()
                .post().uri("/v1/payments/987/refunds")
                .contentType(MediaType.APPLICATION_JSON).body(java.util.Map.of()).retrieve().toBodilessEntity();
        LoggedRequest anterior = primeiraRequisicao();
        assertThat(anterior.getBodyAsString()).isEqualTo("{}");
        assertThat(anterior.getHeader("Transfer-Encoding")).isEqualTo("chunked");
        assertThat(anterior.getHeader("Content-Length")).isNull();
    }

    /** Variante comparativa (não usada em produção): sem corpo, a requisição sai sem Content-Type. */
    @Test void variacaoSemCorpoSeDiferenciaDoObjetoVazio() {
        RestClient.builder().baseUrl(mp.baseUrl()).build().post().uri("/v1/payments/987/refunds")
                .header("Authorization", "Bearer token-teste").header("X-Idempotency-Key", UUID.randomUUID().toString())
                .retrieve().toBodilessEntity();
        LoggedRequest vazio = primeiraRequisicao();
        assertThat(vazio.getBodyAsString()).isEmpty();
        assertThat(vazio.getHeader("Content-Type")).isNull();
    }

    /** Variante comparativa: objeto vazio explícito, escrito à mão, é idêntico ao que o gateway envia. */
    @Test void variacaoComObjetoVazioExplicitoEquivaleAoGateway() {
        RestClient.builder().baseUrl(mp.baseUrl()).build().post().uri("/v1/payments/987/refunds")
                .contentType(MediaType.APPLICATION_JSON).body("{}").retrieve().toBodilessEntity();
        LoggedRequest explicito = primeiraRequisicao();
        assertThat(explicito.getBodyAsString()).isEqualTo("{}");
        assertThat(explicito.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(explicito.getHeader("Content-Length")).isEqualTo("2");
    }
}
