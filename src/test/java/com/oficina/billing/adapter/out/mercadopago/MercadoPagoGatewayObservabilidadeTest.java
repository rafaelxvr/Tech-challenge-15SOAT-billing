package com.oficina.billing.adapter.out.mercadopago;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento.SolicitacaoCobranca;
import com.oficina.billing.config.BillingProperties;
import com.oficina.billing.domain.Dinheiro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Observabilidade sanitizada do gateway: o que é registrado em log e, sobretudo, o que nunca é. */
class MercadoPagoGatewayObservabilidadeTest {
    static final String TOKEN = "APP_USR-1234567890-token-secreto";
    static final String SEGREDO_WEBHOOK = "segredo-webhook-xyz";
    WireMockServer mp;
    MercadoPagoGateway gateway;
    ListAppender<ILoggingEvent> logs;
    Logger logger;

    @BeforeEach void iniciar() {
        mp = new WireMockServer(wireMockConfig().dynamicPort());
        mp.start();
        gateway = new MercadoPagoGateway(RestClient.builder(), new BillingProperties.MercadoPago(mp.baseUrl(), TOKEN,
                SEGREDO_WEBHOOK, "https://oficina.example/billing/webhooks/mercadopago", true, Duration.ofMinutes(5)));
        logger = (Logger) LoggerFactory.getLogger(MercadoPagoGateway.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach void parar() {
        logger.detachAppender(logs);
        mp.stop();
    }

    String saida() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    @Test void sucessoRegistraOperacaoResultadoEStatus() {
        mp.stubFor(post("/v1/payments/987/refunds").willReturn(aResponse().withStatus(201).withBody("{\"id\":1}")));
        gateway.estornar("987", UUID.randomUUID());
        assertThat(saida()).isEqualTo("Mercado Pago operacao=MERCADO_PAGO_ESTORNO resultado=SUCESSO status=201");
    }

    @Test void sucessoDeConsultaERegistradoSemDadosDoPagamento() {
        mp.stubFor(get("/v1/payments/987654321").willReturn(okJson("{\"id\":987654321,\"status\":\"approved\"}")));
        gateway.consultarPagamento("987654321");
        assertThat(saida()).isEqualTo("Mercado Pago operacao=MERCADO_PAGO_CONSULTA resultado=SUCESSO status=200");
    }

    @Test void erro4xxComJsonValidoRegistraApenasCodigoEMensagemPermitidos() {
        mp.stubFor(post("/v1/payments/987654321/refunds").willReturn(aResponse().withStatus(400).withBody(
                "{\"message\":\"Payment 987654321 cannot be refunded\",\"error\":\"bad_request\",\"status\":400,"
                        + "\"cause\":[{\"code\":2004,\"description\":\"x\"}],\"access_token\":\"" + TOKEN + "\","
                        + "\"user_id\":\"55512345\"}")));
        assertThatThrownBy(() -> gateway.estornar("987654321", UUID.randomUUID()))
                .isInstanceOf(GatewayIndisponivelException.class)
                .hasMessage("MERCADO_PAGO_ESTORNO_FALHOU");
        assertThat(saida()).isEqualTo("Mercado Pago operacao=MERCADO_PAGO_ESTORNO resultado=FALHA status=400 "
                + "codigoProvedor=bad_request mensagemProvedor=\"x\"");
    }

    @Test void erro5xxUsaCodigoDaCausaQuandoNaoHaErro() {
        mp.stubFor(post("/checkout/preferences").willReturn(aResponse().withStatus(503)
                .withBody("{\"cause\":[{\"code\":1000,\"description\":\"Servico indisponivel\"}]}")));
        assertThatThrownBy(() -> gateway.criarCobranca(solicitacao())).isInstanceOf(GatewayIndisponivelException.class);
        assertThat(saida()).isEqualTo("Mercado Pago operacao=MERCADO_PAGO_PREFERENCIA resultado=FALHA status=503 "
                + "codigoProvedor=1000 mensagemProvedor=\"Servico indisponivel\"");
    }

    @Test void corpoInvalidoNaoVazaNada() {
        mp.stubFor(post("/v1/payments/1/refunds").willReturn(aResponse().withStatus(502)
                .withBody("<html>Bearer " + TOKEN + " user=55512345</html>")));
        assertThatThrownBy(() -> gateway.estornar("1", UUID.randomUUID())).isInstanceOf(GatewayIndisponivelException.class);
        assertThat(saida()).isEqualTo("Mercado Pago operacao=MERCADO_PAGO_ESTORNO resultado=FALHA status=502 "
                + "codigoProvedor=- mensagemProvedor=\"-\"");
    }

    @Test void corpoJsonQueNaoEObjetoEIgnorado() {
        mp.stubFor(get("/v1/payments/1").willReturn(aResponse().withStatus(500).withBody("[\"message\",\"x\"]")));
        assertThatThrownBy(() -> gateway.consultarPagamento("1")).isInstanceOf(GatewayIndisponivelException.class);
        assertThat(saida()).contains("status=500 codigoProvedor=- mensagemProvedor=\"-\"");
    }

    @Test void mensagemSemControleCrlfETruncada() {
        String longa = "z".repeat(500);
        mp.stubFor(post("/v1/payments/1/refunds").willReturn(aResponse().withStatus(400).withBody(
                "{\"message\":\"linha1\\r\\nFORJADO\\u0000\\u0085\\u2028 fim " + longa + "\",\"error\":\"bad\\r\\nrequest\"}")));
        assertThatThrownBy(() -> gateway.estornar("1", UUID.randomUUID())).isInstanceOf(GatewayIndisponivelException.class);
        String linha = saida();
        assertThat(linha).doesNotContain("\r").doesNotContain("\n").doesNotContain(String.valueOf((char) 0))
                .doesNotContain(String.valueOf((char) 0x85)).doesNotContain(String.valueOf((char) 0x2028));
        assertThat(linha).contains("codigoProvedor=- ").contains("linha1 FORJADO");
        assertThat(linha).endsWith("...\"");
        assertThat(linha.length()).isLessThan(ErroProvedorMercadoPago.LIMITE_MENSAGEM + 200);
    }

    @Test void ecoDeSegredosEOmitidoDaMensagem() {
        mp.stubFor(post("/v1/payments/1/refunds").willReturn(aResponse().withStatus(401).withBody(
                "{\"message\":\"token " + TOKEN + " e " + SEGREDO_WEBHOOK + " Authorization: Bearer abc.def.ghi em "
                        + "https://api.mercadopago.com/v1/payments/55512345?access_token=zzz id "
                        + "123e4567-e89b-12d3-a456-426614174000\"}")));
        assertThatThrownBy(() -> gateway.estornar("1", UUID.randomUUID())).isInstanceOf(GatewayIndisponivelException.class);
        assertThat(saida()).doesNotContain(TOKEN).doesNotContain(SEGREDO_WEBHOOK).doesNotContain("abc.def.ghi")
                .doesNotContain("55512345").doesNotContain("access_token").doesNotContain("123e4567")
                .doesNotContain("Bearer").endsWith("mensagemProvedor=\"-\"");
    }

    @Test void logsNaoTrazemAuthorizationQueryStringNemStackTrace() {
        mp.stubFor(get(urlPathEqualTo("/v1/payments/search")).willReturn(aResponse().withStatus(500).withBody("{}")));
        UUID ref = UUID.randomUUID();
        assertThatThrownBy(() -> gateway.buscarPorReferencia(ref)).isInstanceOf(GatewayIndisponivelException.class);
        assertThat(saida()).doesNotContain(TOKEN).doesNotContain(ref.toString()).doesNotContain("external_reference");
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
    }

    @Test void conexaoRompidaFicaComoFalhaSemStatus() {
        mp.stubFor(post("/v1/payments/1/refunds").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> gateway.estornar("1", UUID.randomUUID()))
                .isInstanceOf(GatewayIndisponivelException.class).hasMessage("MERCADO_PAGO_ESTORNO_FALHOU");
        assertThat(saida()).startsWith("Mercado Pago operacao=MERCADO_PAGO_ESTORNO resultado=FALHA status=- tipo=")
                .doesNotContain("localhost").doesNotContain("127.0.0.1").doesNotContain(String.valueOf(mp.port()));
    }

    @Test void timeoutDeLeituraFicaComoFalhaSemStatus() {
        mp.stubFor(post("/v1/payments/1/refunds").willReturn(aResponse().withStatus(201).withFixedDelay(5_600)));
        assertThatThrownBy(() -> gateway.estornar("1", UUID.randomUUID()))
                .isInstanceOf(GatewayIndisponivelException.class).hasMessage("MERCADO_PAGO_ESTORNO_FALHOU");
        assertThat(saida()).isEqualTo(
                "Mercado Pago operacao=MERCADO_PAGO_ESTORNO resultado=FALHA status=- tipo=SocketTimeoutException");
    }

    @Test void respostaDeSucessoSemCorpoEmOperacaoQueExigeCorpoEVazia() {
        mp.stubFor(get("/v1/payments/1").willReturn(aResponse().withStatus(200)));
        assertThatThrownBy(() -> gateway.consultarPagamento("1"))
                .isInstanceOf(GatewayIndisponivelException.class).hasMessage("MERCADO_PAGO_CONSULTA_VAZIO");
        assertThat(saida()).contains("resultado=VAZIO status=200");
    }

    private static SolicitacaoCobranca solicitacao() {
        return new SolicitacaoCobranca(UUID.randomUUID(), UUID.randomUUID(), 1, Dinheiro.de("1"),
                Instant.parse("2026-09-15T12:30:00Z"));
    }
}
