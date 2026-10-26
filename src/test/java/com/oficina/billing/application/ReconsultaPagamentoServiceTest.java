package com.oficina.billing.application;

import com.oficina.billing.adapter.out.fake.GatewayPagamentoFake;
import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.application.port.GatewayPagamento.StatusExterno;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Re-query fallback (no webhook tunnel): applies the provider state through the verified confirmation path. */
@Import({GerarOrcamentoUseCase.class, RegistrarDecisaoUseCase.class, CancelarOrcamentoUseCase.class,
        ConfirmacaoPagamentoService.class, ReconsultaPagamentoService.class, EmissorRespostas.class, FabricaRespostas.class,
        JdbcOutbox.class, MapeadorMensagens.class, ApoioCasosDeUso.class})
class ReconsultaPagamentoServiceTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired RegistrarDecisaoUseCase decidir;
    @Autowired CancelarOrcamentoUseCase cancelar;
    @Autowired ReconsultaPagamentoService reconsulta;
    @Autowired PagamentoRepository pagamentos;
    @Autowired MapeadorMensagens mapeador;
    @Autowired GatewayPagamentoFake gateway;
    UUID os;
    UUID pagamentoId;

    @BeforeEach void aguardandoPagamento() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"))));
        pagamentoId = UUID.fromString(RespostasEmitidas.de(jdbc, mapeador, os).get(1).payload().get("pagamentoId").asText());
    }

    @Test void semPagamentoNoProvedorNaoMudaNada() {
        assertThat(reconsulta.reconsultar(pagamentoId)).isFalse();
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.PENDENTE);
    }

    @Test void pagamentoAprovadoNoProvedorConfirmaERespondeUmaVez() {
        gateway.simular(pagamentoId, StatusExterno.APROVADO);
        assertThat(reconsulta.reconsultar(pagamentoId)).isTrue();
        assertThat(reconsulta.reconsultar(pagamentoId)).isFalse();
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.CONFIRMADO);
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).extracting(e -> e.type())
                .containsExactly("OrcamentoGerado", "OrcamentoAprovado", "PagamentoConfirmado");
    }

    @Test void valorDivergenteNoProvedorNaoConfirma() {
        gateway.simular(pagamentoId, StatusExterno.APROVADO, new java.math.BigDecimal("0.01"), "BRL");
        reconsulta.reconsultar(pagamentoId);
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.PENDENTE);
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).hasSize(2);
    }

    @Test void pagamentoTardioDeCobrancaCanceladaEEstornado() {
        transaction.executeWithoutResult(st -> cancelar.executar(Comandos.comando("CancelarOrcamento", os, 4,
                Comandos.compensacao(1, "PAGAMENTO_EXPIRADO"))));
        String mpId = gateway.simular(pagamentoId, StatusExterno.APROVADO);
        assertThat(reconsulta.reconsultar(pagamentoId)).isTrue();
        assertThat(gateway.estornado(mpId)).isTrue();
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.ESTORNADO);
    }

    @Test void reconsultarPendentesAplicaSomenteOQueOProvedorTem() {
        assertThat(reconsulta.reconsultarPendentes(10)).isZero();
        gateway.simular(pagamentoId, StatusExterno.RECUSADO);
        assertThat(reconsulta.reconsultarPendentes(10)).isEqualTo(1);
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.RECUSADO);
    }

    @Test void pagamentoDesconhecidoFalha() {
        assertThatThrownBy(() -> reconsulta.reconsultar(UUID.randomUUID())).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
