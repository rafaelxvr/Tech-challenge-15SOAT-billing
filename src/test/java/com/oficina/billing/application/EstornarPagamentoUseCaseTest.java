package com.oficina.billing.application;

import com.oficina.billing.adapter.out.fake.GatewayPagamentoFake;
import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
import com.oficina.billing.domain.EstadoPagamento;
import com.oficina.billing.mensagem.FabricaRespostas;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({GerarOrcamentoUseCase.class, RegistrarDecisaoUseCase.class, EstornarPagamentoUseCase.class,
        EmissorRespostas.class, FabricaRespostas.class, JdbcOutbox.class, MapeadorMensagens.class, ApoioCasosDeUso.class})
class EstornarPagamentoUseCaseTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired RegistrarDecisaoUseCase decidir;
    @Autowired EstornarPagamentoUseCase estornar;
    @Autowired PagamentoRepository pagamentos;
    @Autowired MapeadorMensagens mapeador;
    @Autowired GatewayPagamentoFake gateway;
    UUID os;
    UUID pagamentoId;

    @BeforeEach void pagamentoConfirmado() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"))));
        pagamentoId = UUID.fromString(RespostasEmitidas.de(jdbc, mapeador, os).get(1).payload().get("pagamentoId").asText());
        String mpId = gateway.simular(pagamentoId, com.oficina.billing.application.port.GatewayPagamento.StatusExterno.APROVADO);
        transaction.executeWithoutResult(st -> pagamentos.findById(pagamentoId).orElseThrow().confirmar(mpId, Comandos.T0));
    }

    @AfterEach void restaurar() { gateway.indisponibilizarEstornos(false); }

    @Test void estornaNoGatewayERespondePagamentoEstornado() {
        var cmd = Comandos.comando("EstornarPagamento", os, 5, Comandos.compensacao(1, "ESTOQUE_INSUFICIENTE"));
        transaction.executeWithoutResult(st -> estornar.executar(cmd));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(2);
        assertThat(r.type()).isEqualTo("PagamentoEstornado");
        assertThat(r.payload().get("pagamentoId").asText()).isEqualTo(pagamentoId.toString());
        transaction.executeWithoutResult(st -> {
            var p = pagamentos.findById(pagamentoId).orElseThrow();
            assertThat(p.getEstado()).isEqualTo(EstadoPagamento.ESTORNADO);
            assertThat(gateway.estornado(p.getMpPaymentId())).isTrue();
        });
    }

    @Test void repeticaoNaoEstornaDuasVezesEReemiteResposta() {
        transaction.executeWithoutResult(st -> estornar.executar(Comandos.comando("EstornarPagamento", os, 5, Comandos.compensacao(1, "REPARO_RECUSADO"))));
        transaction.executeWithoutResult(st -> estornar.executar(Comandos.comando("EstornarPagamento", os, 5, Comandos.compensacao(1, "REPARO_RECUSADO"))));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).extracting(e -> e.type())
                .containsExactly("OrcamentoGerado", "OrcamentoAprovado", "PagamentoEstornado", "PagamentoEstornado");
    }

    @Test void gatewayIndisponivelRespondeCompensacaoFalhou() {
        gateway.indisponibilizarEstornos(true);
        transaction.executeWithoutResult(st -> estornar.executar(Comandos.comando("EstornarPagamento", os, 5, Comandos.compensacao(1, "ESTOQUE_INSUFICIENTE"))));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(2);
        assertThat(r.type()).isEqualTo("CompensacaoFalhou");
        assertThat(r.payload().get("compensacao").asText()).isEqualTo("EstornarPagamento");
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.CONFIRMADO);
    }

    @Test void semPagamentoConfirmadoRespondeEstornadoSemChamarGateway() {
        UUID outraOs = UUID.randomUUID();
        transaction.executeWithoutResult(st -> estornar.executar(Comandos.comando("EstornarPagamento", outraOs, 5, Comandos.compensacao(1, "ESTOQUE_INSUFICIENTE"))));
        var r = RespostasEmitidas.de(jdbc, mapeador, outraOs).get(0);
        assertThat(r.type()).isEqualTo("PagamentoEstornado");
        assertThat(r.payload().get("pagamentoId").isNull()).isTrue();
    }
}
