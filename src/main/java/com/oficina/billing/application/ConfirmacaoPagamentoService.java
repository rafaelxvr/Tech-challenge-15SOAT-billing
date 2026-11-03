package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.application.port.GatewayPagamento.SituacaoPagamento;
import com.oficina.billing.application.port.GatewayPagamento.StatusExterno;
import com.oficina.billing.domain.EstadoPagamento;
import com.oficina.billing.domain.Pagamento;
import com.oficina.billing.mensagem.PayloadsResposta;
import com.oficina.billing.mensagem.TipoResposta;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Applies a provider notification after re-reading the payment from the provider (never trusts the webhook body). */
@Service
@RequiredArgsConstructor
public class ConfirmacaoPagamentoService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfirmacaoPagamentoService.class);
    private final GatewayPagamento gateway;
    private final PagamentoRepository pagamentos;
    private final EmissorRespostas emissor;
    private final Clock clock;

    @Transactional
    public void processarNotificacao(String mpPaymentId) {
        SituacaoPagamento situacao = gateway.consultarPagamento(mpPaymentId);
        if (situacao.status() == StatusExterno.PENDENTE || situacao.pagamentoId() == null) return;
        Pagamento pagamento = pagamentos.findParaAtualizar(situacao.pagamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento", situacao.pagamentoId()));
        Instant agora = clock.instant();
        if (situacao.status() == StatusExterno.APROVADO) aprovado(pagamento, situacao, agora);
        else recusado(pagamento, situacao, agora);
    }

    private void aprovado(Pagamento pagamento, SituacaoPagamento situacao, Instant agora) {
        if (pagamento.encerradoSemCobranca()) {
            gateway.estornar(situacao.mpPaymentId(), pagamento.getId());
            pagamento.estornarConfirmacaoTardia(situacao.mpPaymentId(), agora);
            registrar("billing_confirmacao_tardia_estornada");
            return;
        }
        if (pagamento.getEstado() != EstadoPagamento.PENDENTE) return;
        if (!confere(pagamento, situacao)) {
            // Money moved for a different amount or currency than the charge: never confirm the saga step on it.
            gateway.estornar(situacao.mpPaymentId(), pagamento.getId());
            registrarErro("billing_pagamento_divergente", "PAGAMENTO_VALOR_OU_MOEDA_DIVERGENTE");
            return;
        }
        pagamento.confirmar(situacao.mpPaymentId(), agora);
        emissor.emitir(pagamento.getOrigem(), TipoResposta.PAGAMENTO_CONFIRMADO, PayloadsResposta.pagamento(pagamento));
    }

    private void recusado(Pagamento pagamento, SituacaoPagamento situacao, Instant agora) {
        if (pagamento.getEstado() != EstadoPagamento.PENDENTE) return;
        pagamento.recusar(situacao.mpPaymentId(), agora);
        emissor.emitir(pagamento.getOrigem(), TipoResposta.PAGAMENTO_RECUSADO, PayloadsResposta.pagamento(pagamento));
    }

    private static boolean confere(Pagamento pagamento, SituacaoPagamento situacao) {
        return situacao.valor() != null && situacao.valor().compareTo(pagamento.getValor()) == 0
                && "BRL".equals(situacao.moeda());
    }

    private static void registrarErro(String evento, String codigo) {
        MDC.put("event_name", evento);
        MDC.put("error_code", codigo);
        try { LOG.warn("safe"); } finally { MDC.remove("event_name"); MDC.remove("error_code"); }
    }

    private static void registrar(String evento) {
        MDC.put("event_name", evento);
        try { LOG.info(""); } finally { MDC.remove("event_name"); }
    }
}
