package com.oficina.billing.application.port;

import com.oficina.billing.domain.Dinheiro;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Anti-corruption port: provider types never cross this boundary. */
public interface GatewayPagamento {
    Cobranca criarCobranca(SolicitacaoCobranca solicitacao);

    SituacaoPagamento consultarPagamento(String mpPaymentId);

    void estornar(String mpPaymentId, UUID chaveIdempotencia);

    record SolicitacaoCobranca(UUID pagamentoId, UUID orcamentoId, long numeroOs, Dinheiro valor, Instant expiraEm) {}

    record Cobranca(String preferenceId, String checkoutUrl) {}

    /** Provider-side view of a payment, re-read from the provider: amount and currency are what was actually charged. */
    record SituacaoPagamento(String mpPaymentId, UUID pagamentoId, StatusExterno status, BigDecimal valor, String moeda) {}

    enum StatusExterno { APROVADO, RECUSADO, PENDENTE }
}
