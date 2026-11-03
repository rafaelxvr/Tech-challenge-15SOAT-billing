package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.domain.EstadoPagamento;
import com.oficina.billing.domain.Pagamento;
import com.oficina.billing.mensagem.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Compensation; idempotent by osId + versaoOrcamento. Provider outage after retries → CompensacaoFalhou. */
@Component
@RequiredArgsConstructor
public class EstornarPagamentoUseCase implements ManipuladorComando {
    private static final Logger LOG = LoggerFactory.getLogger(EstornarPagamentoUseCase.class);
    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final TentativasGateway tentativas;
    private final MapeadorMensagens mapeador;
    private final EmissorRespostas emissor;
    private final Clock clock;

    @Override public TipoComando tipo() { return TipoComando.ESTORNAR_PAGAMENTO; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void executar(Envelope comando) {
        CompensacaoPayload p = mapeador.converter(comando.payload(), CompensacaoPayload.class);
        var origem = FabricaRespostas.referencia(comando);
        Instant agora = clock.instant();
        Optional<Pagamento> encontrado = orcamentos.findByOsIdAndVersao(comando.osId(), p.versaoOrcamento())
                .flatMap(o -> pagamentos.findByOrcamentoId(o.getId()));
        if (encontrado.isEmpty()) {
            emissor.emitir(origem, TipoResposta.PAGAMENTO_ESTORNADO, PayloadsResposta.pagamentoEstornado(null));
            return;
        }
        Pagamento pagamento = encontrado.get();
        if (pagamento.getEstado() == EstadoPagamento.CONFIRMADO) {
            try {
                tentativas.executar(() -> gateway.estornar(pagamento.getMpPaymentId(), pagamento.getId()));
            } catch (GatewayIndisponivelException e) {
                MDC.put("event_name", "billing_estorno_falhou");
                MDC.put("error_code", e.codigo());
                try { LOG.warn(""); } finally { MDC.remove("event_name"); MDC.remove("error_code"); }
                emissor.emitir(origem, TipoResposta.COMPENSACAO_FALHOU, PayloadsResposta.compensacaoFalhou("EstornarPagamento"));
                return;
            }
            pagamento.estornar(agora);
        } else {
            pagamento.cancelar(agora);
        }
        emissor.emitir(origem, TipoResposta.PAGAMENTO_ESTORNADO, PayloadsResposta.pagamentoEstornado(pagamento.getId()));
    }
}
