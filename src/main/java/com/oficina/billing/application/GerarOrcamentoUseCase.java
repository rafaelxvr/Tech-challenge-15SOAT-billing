package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.domain.*;
import com.oficina.billing.mensagem.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GerarOrcamentoUseCase implements ManipuladorComando {
    private final OrcamentoRepository orcamentos;
    private final MapeadorMensagens mapeador;
    private final EmissorRespostas emissor;
    private final Clock clock;

    @Override public TipoComando tipo() { return TipoComando.GERAR_ORCAMENTO; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void executar(Envelope comando) {
        GerarOrcamentoPayload p = mapeador.converter(comando.payload(), GerarOrcamentoPayload.class);
        ReferenciaComando origem = FabricaRespostas.referencia(comando);
        Optional<Orcamento> existente = orcamentos.findByOsIdAndVersao(comando.osId(), p.versaoOrcamento());
        if (existente.isPresent()) {
            emissor.emitir(origem, TipoResposta.ORCAMENTO_GERADO, PayloadsResposta.orcamentoGerado(existente.get()));
            return;
        }
        Orcamento orcamento;
        try {
            orcamento = Orcamento.gerar(comando.osId(), comando.sagaId(), p.clienteId(), p.numero(),
                    p.versaoOrcamento(), itens(p), clock.instant());
        } catch (RegraNegocioException | IllegalArgumentException e) {
            emissor.emitir(origem, TipoResposta.ORCAMENTO_FALHOU, PayloadsResposta.orcamentoFalhou());
            return;
        }
        orcamentos.save(orcamento);
        emissor.emitir(origem, TipoResposta.ORCAMENTO_GERADO, PayloadsResposta.orcamentoGerado(orcamento));
    }

    private static List<ItemOrcamento> itens(GerarOrcamentoPayload p) {
        return p.itens().stream().map(i -> new ItemOrcamento(TipoItem.valueOf(i.tipo()), i.referenciaId(),
                i.descricao(), i.quantidade(), new Dinheiro(i.precoUnitario()))).toList();
    }
}
