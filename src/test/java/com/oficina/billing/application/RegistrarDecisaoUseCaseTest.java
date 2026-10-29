package com.oficina.billing.application;

import com.oficina.billing.adapter.out.fake.GatewayPagamentoFake;
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

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({GerarOrcamentoUseCase.class, RegistrarDecisaoUseCase.class, EmissorRespostas.class, FabricaRespostas.class,
        JdbcOutbox.class, MapeadorMensagens.class, ApoioCasosDeUso.class})
class RegistrarDecisaoUseCaseTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired RegistrarDecisaoUseCase decidir;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired PagamentoRepository pagamentos;
    @Autowired MapeadorMensagens mapeador;
    @Autowired GatewayPagamentoFake gateway;
    UUID os;

    @BeforeEach void orcamentoGerado() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
    }

    @Test void aprovacaoCriaCobrancaERespondeComCheckout() {
        var cmd = Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"));
        transaction.executeWithoutResult(st -> decidir.executar(cmd));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(1);
        assertThat(r.type()).isEqualTo("OrcamentoAprovado");
        assertThat(r.causationId()).isEqualTo(cmd.messageId());
        assertThat(r.passo()).isEqualTo(3);
        UUID pagamentoId = UUID.fromString(r.payload().get("pagamentoId").asText());
        assertThat(r.payload().get("checkoutUrl").asText()).isEqualTo("https://pagamento.fake.local/checkout/" + pagamentoId);
        transaction.executeWithoutResult(st -> {
            var pagamento = pagamentos.findById(pagamentoId).orElseThrow();
            assertThat(pagamento.getEstado()).isEqualTo(EstadoPagamento.PENDENTE);
            assertThat(pagamento.getOrigem().getComandoId()).isEqualTo(cmd.messageId());
            assertThat(pagamento.getExpiraEm()).isEqualTo(Comandos.T0.plus(Duration.ofMinutes(30)));
            assertThat(orcamentos.findByOsIdAndVersao(os, 1).orElseThrow().getEstado()).isEqualTo(EstadoOrcamento.APROVADO);
        });
    }

    @Test void reenvioDaAprovacaoNaoCriaSegundaCobranca() {
        int cobrancasAntes = gateway.cobrancasCriadas();
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"))));
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"))));
        var respostas = RespostasEmitidas.de(jdbc, mapeador, os);
        assertThat(respostas).extracting(e -> e.type()).containsExactly("OrcamentoGerado", "OrcamentoAprovado", "OrcamentoAprovado");
        assertThat(respostas.get(1).payload().get("pagamentoId")).isEqualTo(respostas.get(2).payload().get("pagamentoId"));
        assertThat(gateway.cobrancasCriadas() - cobrancasAntes).isEqualTo(1);
        assertThat(pagamentos.count()).isEqualTo(1);
    }

    @Test void recusaRespondeOrcamentoRecusadoDeFormaIdempotente() {
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "RECUSADO"))));
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "RECUSADO"))));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).extracting(e -> e.type())
                .containsExactly("OrcamentoGerado", "OrcamentoRecusado", "OrcamentoRecusado");
        assertThat(gateway.cobrancasCriadas()).isZero();
    }

    @Test void decisaoParaVersaoInexistenteFalhaSemResposta() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(st ->
                decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(9, "APROVADO")))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).hasSize(1);
    }
}
