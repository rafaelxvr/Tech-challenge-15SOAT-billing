package com.oficina.billing.mensagem;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapeadorMensagensTest {
    final MapeadorMensagens mapeador = new MapeadorMensagens();

    Envelope comando() {
        return new Envelope(UUID.randomUUID(), Envelope.Kind.COMMAND, "RegistrarDecisao", 1, UUID.randomUUID(),
                UUID.randomUUID(), 3, null, Instant.parse("2026-09-15T12:00:00Z"), UUID.randomUUID().toString(), null,
                JsonNodeFactory.instance.objectNode().put("versaoOrcamento", 1).put("decisao", "APROVADO"));
    }

    @Test void idaEVoltaPreservaCampos() {
        var original = comando();
        assertThat(mapeador.ler(mapeador.escrever(original))).isEqualTo(original);
    }

    @Test void converteEValidaPayload() {
        var decisao = mapeador.converter(comando().payload(), RegistrarDecisaoPayload.class);
        assertThat(decisao.aprovado()).isTrue();
        assertThatThrownBy(() -> mapeador.converter(JsonNodeFactory.instance.objectNode().put("versaoOrcamento", 1)
                .put("decisao", "TALVEZ"), RegistrarDecisaoPayload.class)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapeador.converter(JsonNodeFactory.instance.objectNode(), CompensacaoPayload.class))
                .isInstanceOf(MensagemInvalidaException.class);
    }

    @Test void invalidoERejeitado() {
        assertThatThrownBy(() -> mapeador.ler("{nao-json")).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapeador.ler("{\"type\":\"X\"}")).isInstanceOf(MensagemInvalidaException.class);
    }

    @Test void vocabularioUsaNomesDoContrato() {
        assertThat(TipoComando.deTipo("EstornarPagamento")).contains(TipoComando.ESTORNAR_PAGAMENTO);
        assertThat(TipoComando.deTipo("IniciarReparo")).isEmpty();
        assertThat(TipoResposta.PAGAMENTO_CONFIRMADO.tipo()).isEqualTo("PagamentoConfirmado");
    }
}
