package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.domain.EstadoOrcamento;
import com.oficina.billing.domain.EstadoPagamento;
import com.oficina.billing.mensagem.FabricaRespostas;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({GerarOrcamentoUseCase.class, RegistrarDecisaoUseCase.class, CancelarOrcamentoUseCase.class,
        EmissorRespostas.class, FabricaRespostas.class, JdbcOutbox.class, MapeadorMensagens.class, ApoioCasosDeUso.class})
class CancelarOrcamentoUseCaseTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired RegistrarDecisaoUseCase decidir;
    @Autowired CancelarOrcamentoUseCase cancelar;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired PagamentoRepository pagamentos;
    @Autowired MapeadorMensagens mapeador;
    UUID os;

    @BeforeEach void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
    }

    @Test void cancelaOrcamentoEPagamentoPendente() {
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"))));
        var cmd = Comandos.comando("CancelarOrcamento", os, 4, Comandos.compensacao(1, "PAGAMENTO_EXPIRADO"));
        transaction.executeWithoutResult(st -> cancelar.executar(cmd));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(2);
        assertThat(r.type()).isEqualTo("OrcamentoCancelado");
        assertThat(r.causationId()).isEqualTo(cmd.messageId());
        transaction.executeWithoutResult(st -> {
            var o = orcamentos.findByOsIdAndVersao(os, 1).orElseThrow();
            assertThat(o.getEstado()).isEqualTo(EstadoOrcamento.CANCELADO);
            assertThat(r.payload().get("orcamentoId").asText()).isEqualTo(o.getId().toString());
            assertThat(pagamentos.findByOrcamentoId(o.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.CANCELADO);
        });
    }

    @Test void semOrcamentoRespondeComIdNulo() {
        transaction.executeWithoutResult(st -> cancelar.executar(Comandos.comando("CancelarOrcamento", os, 2,
                Comandos.compensacao(1, "ORCAMENTO_SEM_RESPOSTA"))));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(0);
        assertThat(r.type()).isEqualTo("OrcamentoCancelado");
        assertThat(r.payload().get("orcamentoId").isNull()).isTrue();
    }

    @Test void repeticaoReemiteMesmaResposta() {
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        transaction.executeWithoutResult(st -> cancelar.executar(Comandos.comando("CancelarOrcamento", os, 3, Comandos.compensacao(1, "DECISAO_EXPIRADA"))));
        transaction.executeWithoutResult(st -> cancelar.executar(Comandos.comando("CancelarOrcamento", os, 3, Comandos.compensacao(1, "DECISAO_EXPIRADA"))));
        var respostas = RespostasEmitidas.de(jdbc, mapeador, os);
        assertThat(respostas).extracting(e -> e.type()).containsExactly("OrcamentoGerado", "OrcamentoCancelado", "OrcamentoCancelado");
        assertThat(respostas.get(1).payload()).isEqualTo(respostas.get(2).payload());
    }
}
