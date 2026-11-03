package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.application.port.GatewayPagamento;
import com.oficina.billing.domain.*;
import com.oficina.billing.mensagem.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RegistrarDecisaoUseCase implements ManipuladorComando {
    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final GatewayPagamento gateway;
    private final PoliticaPagamento politica;
    private final MapeadorMensagens mapeador;
    private final EmissorRespostas emissor;
    private final Clock clock;

    @Override public TipoComando tipo() { return TipoComando.REGISTRAR_DECISAO; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void executar(Envelope comando) {
        RegistrarDecisaoPayload p = mapeador.converter(comando.payload(), RegistrarDecisaoPayload.class);
        ReferenciaComando origem = FabricaRespostas.referencia(comando);
        Orcamento orcamento = orcamentos.findByOsIdAndVersao(comando.osId(), p.versaoOrcamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento da OS", comando.osId()));
        Instant agora = clock.instant();
        if (!p.aprovado()) {
            if (orcamento.getEstado() != EstadoOrcamento.RECUSADO) orcamento.recusar(agora);
            emissor.emitir(origem, TipoResposta.ORCAMENTO_RECUSADO, PayloadsResposta.orcamentoRecusado(orcamento));
            return;
        }
        if (orcamento.getEstado() == EstadoOrcamento.APROVADO) {
            Pagamento existente = pagamentos.findByOrcamentoId(orcamento.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento do orçamento", orcamento.getId()));
            emissor.emitir(origem, TipoResposta.ORCAMENTO_APROVADO, PayloadsResposta.orcamentoAprovado(orcamento, existente));
            return;
        }
        orcamento.aprovar(agora);
        UUID pagamentoId = UUID.randomUUID();
        Instant expiraEm = agora.plus(politica.validade());
        var cobranca = gateway.criarCobranca(new GatewayPagamento.SolicitacaoCobranca(pagamentoId, orcamento.getId(),
                orcamento.getNumeroOs(), orcamento.valorTotal(), expiraEm));
        Pagamento pagamento = Pagamento.abrir(pagamentoId, orcamento, cobranca.preferenceId(), cobranca.checkoutUrl(),
                expiraEm, origem, agora);
        pagamentos.save(pagamento);
        emissor.emitir(origem, TipoResposta.ORCAMENTO_APROVADO, PayloadsResposta.orcamentoAprovado(orcamento, pagamento));
    }
}
