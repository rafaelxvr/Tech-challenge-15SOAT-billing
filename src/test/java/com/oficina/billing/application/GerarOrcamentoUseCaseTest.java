package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.adapter.out.persistence.OrcamentoRepository;
import com.oficina.billing.mensagem.FabricaRespostas;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({GerarOrcamentoUseCase.class, EmissorRespostas.class, FabricaRespostas.class, JdbcOutbox.class,
        MapeadorMensagens.class, ApoioCasosDeUso.class})
class GerarOrcamentoUseCaseTest extends PostgresTeste {
    @Autowired GerarOrcamentoUseCase useCase;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired MapeadorMensagens mapeador;

    @BeforeEach void limpar() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
    }

    @Test void geraOrcamentoERespondeComTotal() {
        UUID os = UUID.randomUUID();
        var cmd = Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1));
        transaction.executeWithoutResult(st -> useCase.executar(cmd));
        var respostas = RespostasEmitidas.de(jdbc, mapeador, os);
        assertThat(respostas).hasSize(1);
        var r = respostas.get(0);
        assertThat(r.type()).isEqualTo("OrcamentoGerado");
        assertThat(r.causationId()).isEqualTo(cmd.messageId());
        assertThat(r.payload().get("total").decimalValue()).isEqualByComparingTo("151.00");
        assertThat(orcamentos.findByOsIdAndVersao(os, 1)).isPresent();
    }

    @Test void reenvioComNovoIdRepeteMesmaRespostaSemDuplicar() {
        UUID os = UUID.randomUUID();
        transaction.executeWithoutResult(st -> useCase.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        transaction.executeWithoutResult(st -> useCase.executar(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        var respostas = RespostasEmitidas.de(jdbc, mapeador, os);
        assertThat(respostas).extracting(e -> e.payload().get("orcamentoId").asText()).hasSize(2).containsOnly(
                respostas.get(0).payload().get("orcamentoId").asText());
        assertThat(orcamentos.findByOsIdOrderByVersaoAsc(os)).hasSize(1);
    }

    @Test void itemInvalidoRespondeOrcamentoFalhou() {
        UUID os = UUID.randomUUID();
        var payload = Comandos.gerarOrcamento(1);
        payload.withArray("itens").add(Comandos.item("PECA", "Negativa", 1, "-1.00"));
        transaction.executeWithoutResult(st -> useCase.executar(Comandos.comando("GerarOrcamento", os, 2, payload)));
        var r = RespostasEmitidas.de(jdbc, mapeador, os).get(0);
        assertThat(r.type()).isEqualTo("OrcamentoFalhou");
        assertThat(r.payload().get("motivo").asText()).isEqualTo("ITENS_INVALIDOS");
        assertThat(orcamentos.findByOsIdAndVersao(os, 1)).isEmpty();
    }

    @Test void tipoDeItemDesconhecidoRespondeOrcamentoFalhou() {
        UUID os = UUID.randomUUID();
        var payload = Comandos.gerarOrcamento(1);
        payload.withArray("itens").add(Comandos.item("BRINDE", "x", 1, "1.00"));
        transaction.executeWithoutResult(st -> useCase.executar(Comandos.comando("GerarOrcamento", os, 2, payload)));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os).get(0).type()).isEqualTo("OrcamentoFalhou");
    }
}
