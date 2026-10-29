package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.adapter.out.persistence.PagamentoRepository;
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

@Import({GerarOrcamentoUseCase.class, RegistrarDecisaoUseCase.class, ExpiracaoPagamentosService.class,
        EmissorRespostas.class, FabricaRespostas.class, JdbcOutbox.class, MapeadorMensagens.class, ApoioCasosDeUso.class})
class ExpiracaoPagamentosServiceTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired RegistrarDecisaoUseCase decidir;
    @Autowired ExpiracaoPagamentosService expiracao;
    @Autowired PagamentoRepository pagamentos;
    @Autowired MapeadorMensagens mapeador;
    @Autowired RelogioAjustavel relogio;
    UUID os;

    @BeforeEach void aguardandoPagamento() {
        relogio.definir(Comandos.T0);
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> gerar.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        transaction.executeWithoutResult(st -> decidir.executar(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO"))));
    }

    @Test void expiraSomenteAposValidadeERespondeUmaVez() {
        relogio.avancar(Duration.ofMinutes(29));
        assertThat(expiracao.expirarVencidos(20)).isZero();
        relogio.avancar(Duration.ofMinutes(1));
        assertThat(expiracao.expirarVencidos(20)).isEqualTo(1);
        assertThat(expiracao.expirarVencidos(20)).isZero();
        var respostas = RespostasEmitidas.de(jdbc, mapeador, os);
        assertThat(respostas).extracting(e -> e.type()).containsExactly("OrcamentoGerado", "OrcamentoAprovado", "PagamentoExpirado");
        assertThat(pagamentos.findAll()).singleElement().extracting(p -> p.getEstado()).isEqualTo(EstadoPagamento.EXPIRADO);
    }
}
