package com.oficina.billing.adapter.out.fake;

import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Profile gateway-fake: deterministic checkout URLs and payments driven by the simulation endpoint. */
public class GatewayPagamentoFake implements GatewayPagamento {
    private final Map<String, SituacaoPagamento> pagamentos = new ConcurrentHashMap<>();
    private final Set<String> estornados = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BigDecimal> valoresCobrados = new ConcurrentHashMap<>();
    private final AtomicInteger cobrancas = new AtomicInteger();
    private volatile boolean estornoIndisponivel;

    @Override public Cobranca criarCobranca(SolicitacaoCobranca solicitacao) {
        cobrancas.incrementAndGet();
        valoresCobrados.put(solicitacao.pagamentoId(), solicitacao.valor().valor());
        return new Cobranca("fake-pref-" + solicitacao.pagamentoId(),
                "https://pagamento.fake.local/checkout/" + solicitacao.pagamentoId());
    }

    @Override public SituacaoPagamento consultarPagamento(String mpPaymentId) {
        SituacaoPagamento situacao = pagamentos.get(mpPaymentId);
        if (situacao == null) throw new GatewayIndisponivelException("FAKE_PAGAMENTO_DESCONHECIDO");
        return situacao;
    }

    @Override public Optional<SituacaoPagamento> buscarPorReferencia(UUID pagamentoId) {
        return pagamentos.values().stream().filter(s -> pagamentoId.equals(s.pagamentoId())).findFirst();
    }

    @Override public void estornar(String mpPaymentId, UUID chaveIdempotencia) {
        if (estornoIndisponivel) throw new GatewayIndisponivelException("FAKE_ESTORNO_INDISPONIVEL");
        estornados.add(mpPaymentId);
    }

    /** Simulates a payment for exactly the amount that was charged, in BRL. */
    public String simular(UUID pagamentoId, StatusExterno status) {
        return simular(pagamentoId, status, valoresCobrados.get(pagamentoId), "BRL");
    }

    public String simular(UUID pagamentoId, StatusExterno status, BigDecimal valor, String moeda) {
        String id = "fake-pay-" + UUID.randomUUID();
        pagamentos.put(id, new SituacaoPagamento(id, pagamentoId, status, valor, moeda));
        return id;
    }

    public boolean estornado(String mpPaymentId) { return estornados.contains(mpPaymentId); }

    public void indisponibilizarEstornos(boolean indisponivel) { this.estornoIndisponivel = indisponivel; }

    public int cobrancasCriadas() { return cobrancas.get(); }
}
