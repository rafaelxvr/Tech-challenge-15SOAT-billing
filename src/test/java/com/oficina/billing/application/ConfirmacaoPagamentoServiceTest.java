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

@Import({GerarOrcamentoUseCase.class, RegistrarDecisaoUseCase.class, CancelarOrcamentoUseCase.class,
        ConfirmacaoPagamentoService.class, EmissorRespostas.class, FabricaRespostas.class, JdbcOutbox.class,
        MapeadorMensagens.class, ApoioCasosDeUso.class})
class ConfirmacaoPagamentoServiceTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired RegistrarDecisaoUseCase decidir;
    @Autowired CancelarOrcamentoUseCase cancelar;
    @Autowired ConfirmacaoPagamentoService confirmacao;
    @Autowired PagamentoRepository pagamentos;
    @Autowired MapeadorMensagens mapeador;
    @Autowired GatewayPagamentoFake gateway;
    UUID os;
    UUID decisaoId;
    UUID pagamentoId;

    @BeforeEach void aguardandoPagamento() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        var decisao = Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"));
        decisaoId = decisao.messageId();
        transaction.executeWithoutResult(st -> decidir.executar(decisao));
        pagamentoId = UUID.fromString(RespostasEmitidas.de(jdbc, mapeador, os).get(1).payload().get("pagamentoId").asText());
    }

    @Test void aprovadoConfirmaERespondeACausaDaDecisao() {
        confirmacao.processarNotificacao(gateway.simular(pagamentoId, StatusExterno.APROVADO));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(2);
        assertThat(r.type()).isEqualTo("PagamentoConfirmado");
        assertThat(r.causationId()).isEqualTo(decisaoId);
        assertThat(r.passo()).isEqualTo(3);
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.CONFIRMADO);
    }

    @Test void valorOuMoedaDivergenteNaoConfirmaEEstornaOCobrado() {
        String valorErrado = gateway.simular(pagamentoId, StatusExterno.APROVADO, new java.math.BigDecimal("1.00"), "BRL");
        confirmacao.processarNotificacao(valorErrado);
        String moedaErrada = gateway.simular(pagamentoId, StatusExterno.APROVADO,
                pagamentos.findById(pagamentoId).orElseThrow().getValor(), "USD");
        confirmacao.processarNotificacao(moedaErrada);
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.PENDENTE);
        assertThat(gateway.estornado(valorErrado)).isTrue();
        assertThat(gateway.estornado(moedaErrada)).isTrue();
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).hasSize(2);
    }

    @Test void notificacaoDuplicadaNaoRespondeDuasVezes() {
        String mpId = gateway.simular(pagamentoId, StatusExterno.APROVADO);
        confirmacao.processarNotificacao(mpId);
        confirmacao.processarNotificacao(mpId);
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).hasSize(3);
    }

    @Test void recusadoRespondePagamentoRecusado() {
        confirmacao.processarNotificacao(gateway.simular(pagamentoId, StatusExterno.RECUSADO));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os).get(2).type()).isEqualTo("PagamentoRecusado");
        assertThat(pagamentos.findById(pagamentoId).orElseThrow().getEstado()).isEqualTo(EstadoPagamento.RECUSADO);
    }

    @Test void pendenteNaoMudaNada() {
        confirmacao.processarNotificacao(gateway.simular(pagamentoId, StatusExterno.PENDENTE));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).hasSize(2);
    }

    @Test void confirmacaoTardiaEstornaSemResponder() {
        transaction.executeWithoutResult(st -> cancelar.executar(Comandos.comando("CancelarOrcamento", os, 4,
                Comandos.compensacao(1, "PAGAMENTO_EXPIRADO"))));
        String mpId = gateway.simular(pagamentoId, StatusExterno.APROVADO);
        confirmacao.processarNotificacao(mpId);
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).extracting(e -> e.type())
                .containsExactly("OrcamentoGerado", "OrcamentoAprovado", "OrcamentoCancelado");
        assertThat(gateway.estornado(mpId)).isTrue();
        var p = pagamentos.findById(pagamentoId).orElseThrow();
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.ESTORNADO);
        assertThat(p.getMpPaymentId()).isEqualTo(mpId);
    }
}
