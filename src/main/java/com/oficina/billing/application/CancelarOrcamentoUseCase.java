package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.domain.Orcamento;
import com.oficina.billing.mensagem.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Compensation; idempotent by osId + versaoOrcamento. Also cancels a pending charge so it can never be confirmed. */
@Component
@RequiredArgsConstructor
public class CancelarOrcamentoUseCase implements ManipuladorComando {
    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final MapeadorMensagens mapeador;
    private final EmissorRespostas emissor;
    private final Clock clock;

    @Override public TipoComando tipo() { return TipoComando.CANCELAR_ORCAMENTO; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void executar(Envelope comando) {
        CompensacaoPayload p = mapeador.converter(comando.payload(), CompensacaoPayload.class);
        var origem = FabricaRespostas.referencia(comando);
        Optional<Orcamento> encontrado = orcamentos.findByOsIdAndVersao(comando.osId(), p.versaoOrcamento());
        if (encontrado.isEmpty()) {
            emissor.emitir(origem, TipoResposta.ORCAMENTO_CANCELADO, PayloadsResposta.orcamentoCancelado(null));
            return;
        }
        Orcamento orcamento = encontrado.get();
        Instant agora = clock.instant();
        orcamento.cancelar(agora);
        pagamentos.findByOrcamentoId(orcamento.getId()).ifPresent(pagamento -> pagamento.cancelar(agora));
        emissor.emitir(origem, TipoResposta.ORCAMENTO_CANCELADO, PayloadsResposta.orcamentoCancelado(orcamento.getId()));
    }
}
