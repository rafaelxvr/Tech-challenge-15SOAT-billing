package com.oficina.billing.adapter.out.mercadopago;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.config.BillingProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Anti-corruption layer over the Mercado Pago REST API; provider fields never leave this class. */
public class MercadoPagoGateway implements GatewayPagamento {
    private static final DateTimeFormatter DATA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private final RestClient http;
    private final boolean sandbox;
    private final String notificationUrl;

    public MercadoPagoGateway(RestClient.Builder builder, BillingProperties.MercadoPago mp) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(5_000);
        this.http = builder.baseUrl(mp.baseUrl()).requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + mp.accessToken()).build();
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
                .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(Preferencia.class));
        String url = sandbox ? p.sandboxInitPoint() : p.initPoint();
        if (p.id() == null || url == null || !url.startsWith("https://")) {
            throw new GatewayIndisponivelException("MERCADO_PAGO_PREFERENCIA_INVALIDA");
        }
        return new Cobranca(p.id(), url);
    }

    @Override
    public SituacaoPagamento consultarPagamento(String mpPaymentId) {
        PagamentoMp p = chamar("MERCADO_PAGO_CONSULTA", () -> http.get().uri("/v1/payments/{id}", mpPaymentId)
                .retrieve().body(PagamentoMp.class));
        return new SituacaoPagamento(mpPaymentId, uuidOuNulo(p.externalReference()), status(p.status()),
                p.transactionAmount(), p.currencyId());
    }

    @Override
    public Optional<SituacaoPagamento> buscarPorReferencia(UUID pagamentoId) {
        BuscaMp busca = chamar("MERCADO_PAGO_BUSCA", () -> http.get().uri(
                "/v1/payments/search?external_reference={ref}&sort=date_created&criteria=desc", pagamentoId.toString())
                .retrieve().body(BuscaMp.class));
        if (busca.results() == null) return Optional.empty();
        // Prefer an approved payment; otherwise the most recent one (the provider returns newest first).
        return busca.results().stream().filter(p -> p.id() != null)
                .min(java.util.Comparator.comparing((PagamentoMp p) -> !"approved".equals(p.status())))
                .map(p -> new SituacaoPagamento(String.valueOf(p.id()), uuidOuNulo(p.externalReference()),
                        status(p.status()), p.transactionAmount(), p.currencyId()));
    }

    @Override
    public void estornar(String mpPaymentId, UUID chaveIdempotencia) {
        chamar("MERCADO_PAGO_ESTORNO", () -> http.post().uri("/v1/payments/{id}/refunds", mpPaymentId)
                .header("X-Idempotency-Key", chaveIdempotencia.toString()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of()).retrieve().toBodilessEntity());
    }

    private static StatusExterno status(String status) {
        if ("approved".equals(status)) return StatusExterno.APROVADO;
        if ("rejected".equals(status) || "cancelled".equals(status)) return StatusExterno.RECUSADO;
        return StatusExterno.PENDENTE;
    }

    private static UUID uuidOuNulo(String valor) {
        try { return valor == null ? null : UUID.fromString(valor); } catch (IllegalArgumentException e) { return null; }
    }

    private static <T> T chamar(String codigo, java.util.function.Supplier<T> chamada) {
        try {
            T resultado = chamada.get();
            if (resultado == null) throw new GatewayIndisponivelException(codigo + "_VAZIO");
            return resultado;
        } catch (RestClientException e) {
            throw new GatewayIndisponivelException(codigo + "_FALHOU");
        }
    }

    record Preferencia(String id, @JsonProperty("init_point") String initPoint,
                       @JsonProperty("sandbox_init_point") String sandboxInitPoint) {}

    record BuscaMp(java.util.List<PagamentoMp> results) {}

    record PagamentoMp(Long id, String status, @JsonProperty("external_reference") String externalReference,
                       @JsonProperty("transaction_amount") java.math.BigDecimal transactionAmount,
                       @JsonProperty("currency_id") String currencyId) {}
}
