package com.oficina.billing.adapter.out.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.config.BillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Anti-corruption layer over the Mercado Pago REST API; provider fields never leave this class. */
public class MercadoPagoGateway implements GatewayPagamento {
    private static final DateTimeFormatter DATA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    /** Estorno total: objeto JSON vazio, enviado como texto para sair com Content-Length (não chunked). */
    private static final String CORPO_ESTORNO_TOTAL = "{}";
    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoGateway.class);
    private final RestClient http;
    private final List<String> segredos;
    private final boolean sandbox;
    private final String notificationUrl;

    public MercadoPagoGateway(RestClient.Builder builder, BillingProperties.MercadoPago mp) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(5_000);
        this.http = builder.baseUrl(mp.baseUrl()).requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + mp.accessToken()).build();
        this.segredos = List.of(String.valueOf(mp.accessToken()), String.valueOf(mp.webhookSecret()));
        this.sandbox = mp.sandbox();
        this.notificationUrl = mp.notificationUrl();
    }

    @Override
    public Cobranca criarCobranca(SolicitacaoCobranca s) {
        Map<String, Object> corpo = Map.of(
                "items", List.of(Map.of("id", s.orcamentoId().toString(), "title", "Ordem de serviço #" + s.numeroOs(),
                        "quantity", 1, "currency_id", "BRL", "unit_price", s.valor().valor())),
                "external_reference", s.pagamentoId().toString(),
                "notification_url", notificationUrl,
                "expires", true,
                "expiration_date_to", DATA_MP.format(s.expiraEm().atZone(ZONA)));
        Preferencia p = chamar("MERCADO_PAGO_PREFERENCIA", () -> http.post().uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().toEntity(Preferencia.class));
        String url = sandbox ? p.sandboxInitPoint() : p.initPoint();
        if (p.id() == null || url == null || !url.startsWith("https://")) {
            throw new GatewayIndisponivelException("MERCADO_PAGO_PREFERENCIA_INVALIDA");
        }
        return new Cobranca(p.id(), url);
    }

    @Override
    public SituacaoPagamento consultarPagamento(String mpPaymentId) {
        PagamentoMp p = chamar("MERCADO_PAGO_CONSULTA", () -> http.get().uri("/v1/payments/{id}", mpPaymentId)
                .retrieve().toEntity(PagamentoMp.class));
        return new SituacaoPagamento(mpPaymentId, uuidOuNulo(p.externalReference()), status(p.status()),
                p.transactionAmount(), p.currencyId());
    }

    @Override
    public Optional<SituacaoPagamento> buscarPorReferencia(UUID pagamentoId) {
        BuscaMp busca = chamar("MERCADO_PAGO_BUSCA", () -> http.get().uri(
                "/v1/payments/search?external_reference={ref}&sort=date_created&criteria=desc", pagamentoId.toString())
                .retrieve().toEntity(BuscaMp.class));
        if (busca.results() == null) return Optional.empty();
        // Prefer an approved payment; otherwise the most recent one (the provider returns newest first).
        return busca.results().stream().filter(p -> p.id() != null)
                .min(java.util.Comparator.comparing((PagamentoMp p) -> !"approved".equals(p.status())))
                .map(p -> new SituacaoPagamento(String.valueOf(p.id()), uuidOuNulo(p.externalReference()),
                        status(p.status()), p.transactionAmount(), p.currencyId()));
    }

    @Override
    public void estornar(String mpPaymentId, UUID chaveIdempotencia) {
        chamarSemCorpo("MERCADO_PAGO_ESTORNO", () -> http.post().uri("/v1/payments/{id}/refunds", mpPaymentId)
                .header("X-Idempotency-Key", chaveIdempotencia.toString()).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON).body(CORPO_ESTORNO_TOTAL).retrieve().toBodilessEntity());
    }

    private static StatusExterno status(String status) {
        if ("approved".equals(status)) return StatusExterno.APROVADO;
        if ("rejected".equals(status) || "cancelled".equals(status)) return StatusExterno.RECUSADO;
        return StatusExterno.PENDENTE;
    }

    private static UUID uuidOuNulo(String valor) {
        try { return valor == null ? null : UUID.fromString(valor); } catch (IllegalArgumentException e) { return null; }
    }

    private <T> T chamar(String operacao, Supplier<ResponseEntity<T>> chamada) {
        ResponseEntity<T> resposta = executar(operacao, chamada);
        if (resposta.getBody() == null) {
            LOG.warn("Mercado Pago operacao={} resultado=VAZIO status={}", operacao, resposta.getStatusCode().value());
            throw new GatewayIndisponivelException(operacao + "_VAZIO");
        }
        return resposta.getBody();
    }

    private void chamarSemCorpo(String operacao, Supplier<ResponseEntity<Void>> chamada) {
        executar(operacao, chamada);
    }

    /** Executa a chamada registrando operação, resultado e status HTTP (quando houver); nunca registra dados brutos. */
    private <T> ResponseEntity<T> executar(String operacao, Supplier<ResponseEntity<T>> chamada) {
        try {
            ResponseEntity<T> resposta = chamada.get();
            LOG.info("Mercado Pago operacao={} resultado=SUCESSO status={}", operacao, resposta.getStatusCode().value());
            return resposta;
        } catch (RestClientResponseException e) {
            ErroProvedorMercadoPago erro = ErroProvedorMercadoPago.de(e.getResponseBodyAsString(), segredos);
            LOG.warn("Mercado Pago operacao={} resultado=FALHA status={} codigoProvedor={} mensagemProvedor=\"{}\"",
                    operacao, e.getStatusCode().value(), erro.codigo().orElse("-"), erro.mensagem().orElse("-"));
            throw new GatewayIndisponivelException(operacao + "_FALHOU");
        } catch (RestClientException e) {
            // Timeout/conexão/corpo ilegível: sem status HTTP; só o tipo da causa, nunca a mensagem (pode ter URL).
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            LOG.warn("Mercado Pago operacao={} resultado=FALHA status=- tipo={}", operacao, causa.getClass().getSimpleName());
            throw new GatewayIndisponivelException(operacao + "_FALHOU");
        }
    }

    record Preferencia(String id, @JsonProperty("init_point") String initPoint,
                       @JsonProperty("sandbox_init_point") String sandboxInitPoint) {}

    record BuscaMp(java.util.List<PagamentoMp> results) {}

    record PagamentoMp(Long id, String status, @JsonProperty("external_reference") String externalReference,
                       @JsonProperty("transaction_amount") java.math.BigDecimal transactionAmount,
                       @JsonProperty("currency_id") String currencyId) {}
}
