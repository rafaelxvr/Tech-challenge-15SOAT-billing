package com.oficina.billing.application;

import com.oficina.billing.adapter.out.persistence.JdbcInbox;
import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.mensagem.*;
import com.oficina.billing.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({ProcessadorComandos.class, RoteadorComandos.class, JdbcInbox.class, GerarOrcamentoUseCase.class,
        RegistrarDecisaoUseCase.class, CancelarOrcamentoUseCase.class, EstornarPagamentoUseCase.class,
        EmissorRespostas.class, FabricaRespostas.class, JdbcOutbox.class, MapeadorMensagens.class, ApoioCasosDeUso.class})
class ProcessadorComandosTest extends PostgresTeste {
    @Autowired ProcessadorComandos processador;
    @Autowired MapeadorMensagens mapeador;
    UUID os;

    @BeforeEach void limpar() {
        jdbc.update("DELETE FROM inbox");
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM pagamento");
        jdbc.update("DELETE FROM orcamento_item");
        jdbc.update("DELETE FROM orcamento");
        os = UUID.randomUUID();
    }

    @Test void comandoValidoGeraResposta() {
        processador.processar(mapeador.escrever(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1))));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).extracting(Envelope::type).containsExactly("OrcamentoGerado");
    }

    @Test void mesmaMensagemDuasVezesProcessaUmaVez() {
        String corpo = mapeador.escrever(Comandos.comando("GerarOrcamento", os, 2, Comandos.gerarOrcamento(1)));
        processador.processar(corpo);
        processador.processar(corpo);
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).hasSize(1);
    }

    @Test void tipoDesconhecidoEConsumidoSemEfeito() {
        processador.processar(mapeador.escrever(Comandos.comando("IniciarReparo", os, 6, Comandos.compensacao(1, "x"))));
        assertThat(RespostasEmitidas.de(jdbc, mapeador, os)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox", Integer.class)).isEqualTo(1);
    }

    @Test void falhaNoCasoDeUsoDesfazInbox() {
        String corpo = mapeador.escrever(Comandos.comando("RegistrarDecisao", os, 3, Comandos.decisao(1, "APROVADO")));
        assertThatThrownBy(() -> processador.processar(corpo)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox", Integer.class)).isZero();
    }

    @Test void respostaOuLixoSaoInvalidos() {
        var resposta = new Envelope(UUID.randomUUID(), Envelope.Kind.REPLY, "OrcamentoGerado", 1, UUID.randomUUID(), os, 2,
                UUID.randomUUID(), Comandos.T0, UUID.randomUUID().toString(), null, Comandos.decisao(1, "APROVADO"));
        assertThatThrownBy(() -> processador.processar(mapeador.escrever(resposta))).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> processador.processar("{}")).isInstanceOf(MensagemInvalidaException.class);
    }
}
