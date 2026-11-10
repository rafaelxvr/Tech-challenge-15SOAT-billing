package com.oficina.billing.adapter.out.messaging;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.oficina.billing.adapter.out.persistence.JdbcOutbox;
import com.oficina.billing.application.port.EnviadorMensagens;
import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.support.PostgresTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({JdbcOutbox.class, MapeadorMensagens.class})
class OutboxPublisherTest extends PostgresTeste {
    // O outbox usa CURRENT_TIMESTAMP do banco como disponivel_em; o relógio do teste precisa estar à frente dele.
    static final Instant T = Instant.now().plus(java.time.Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    @org.springframework.boot.test.mock.mockito.MockBean com.oficina.billing.application.rastreio.PropagadorTraco propagador;
    @Autowired JdbcOutbox outbox;
    final List<UUID> enviados = new ArrayList<>();

    @BeforeEach void limpar() { jdbc.update("DELETE FROM outbox"); }

    Envelope resposta(UUID osId, String tipo) {
        return new Envelope(UUID.randomUUID(), Envelope.Kind.REPLY, tipo, 1, UUID.randomUUID(), osId, 2, UUID.randomUUID(),
                T, UUID.randomUUID().toString(), null, JsonNodeFactory.instance.objectNode());
    }

    OutboxPublisher publisher(EnviadorMensagens enviador) {
        return new OutboxPublisher(jdbc, transaction, enviador, Clock.fixed(T, ZoneOffset.UTC), new BackoffPublicacao(b -> 0));
    }

    @Test void publicaNaOrdemDaOsEGravaConfirmacao() {
        UUID os = UUID.randomUUID();
        var primeira = resposta(os, "OrcamentoAprovado");
        var segunda = resposta(os, "PagamentoConfirmado");
        transaction.executeWithoutResult(st -> { outbox.registrar(primeira); outbox.registrar(segunda); });
        var p = publisher((osId, messageId, corpo, traco) -> { enviados.add(messageId); return "sqs-" + messageId; });
        assertThat(p.publicarProximo()).isTrue();
        assertThat(p.publicarProximo()).isTrue();
        assertThat(p.publicarProximo()).isFalse();
        assertThat(enviados).containsExactly(primeira.messageId(), segunda.messageId());
        assertThat(jdbc.queryForMap("SELECT estado, queue_message_id FROM outbox WHERE message_id=?", primeira.messageId()))
                .containsEntry("estado", "PUBLISHED").containsEntry("queue_message_id", "sqs-" + primeira.messageId());
    }

    @Test void falhaAtrasaENaoDeixaUltrapassar() {
        UUID os = UUID.randomUUID();
        var primeira = resposta(os, "OrcamentoAprovado");
        transaction.executeWithoutResult(st -> { outbox.registrar(primeira); outbox.registrar(resposta(os, "PagamentoConfirmado")); });
        var p = publisher((osId, messageId, corpo, traco) -> { throw new IllegalStateException("token=secret"); });
        assertThat(p.publicarProximo()).isTrue();
        assertThat(jdbc.queryForMap("SELECT estado, tentativas, ultimo_erro_codigo FROM outbox WHERE message_id=?",
                primeira.messageId())).containsEntry("estado", "PENDING").containsEntry("tentativas", 1)
                .containsEntry("ultimo_erro_codigo", "QUEUE_SEND_FAILED");
        assertThat(p.publicarProximo()).isFalse();
    }

    @Test void falhaDeBancoNaTransacaoAdiaNovaTentativaSemEnviar() {
        var jdbcQuebrado = org.mockito.Mockito.spy(jdbc);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("indisponível"))
                .when(jdbcQuebrado).execute(org.mockito.ArgumentMatchers.startsWith("SET LOCAL statement_timeout"));
        var p = new OutboxPublisher(jdbcQuebrado, transaction, (osId, messageId, corpo, traco) -> {
            enviados.add(messageId);
            return "sqs-" + messageId;
        }, Clock.fixed(T, ZoneOffset.UTC), new BackoffPublicacao(b -> 0));
        assertThat(p.publicarProximo()).isFalse();
        assertThat(p.publicarProximo()).isFalse();
        assertThat(enviados).isEmpty();
    }

    @Test void escritaExigeTransacaoELimiteDeTamanho() {
        UUID os = UUID.randomUUID();
        assertThatThrownBy(() -> outbox.registrar(resposta(os, "OrcamentoGerado")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var grande = new Envelope(UUID.randomUUID(), Envelope.Kind.REPLY, "OrcamentoGerado", 1, UUID.randomUUID(), os, 2,
                UUID.randomUUID(), T, UUID.randomUUID().toString(), null,
                JsonNodeFactory.instance.objectNode().put("x", "a".repeat(Envelope.MAX_BYTES)));
        assertThatThrownBy(() -> transaction.executeWithoutResult(st -> outbox.registrar(grande)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void carrierCapturadoNaTransacaoConsumidoraChegaAoEnvioSemPoluirOCorpo() {
        var carrier = new LinkedHashMap<String, String>();
        carrier.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        carrier.put("tracestate", "123@nr=0-0-1-2-3");
        org.mockito.Mockito.when(propagador.capturar()).thenReturn(carrier);
        var resp = resposta(UUID.randomUUID(), "OrcamentoGerado");
        transaction.executeWithoutResult(st -> {
            outbox.registrar(resp);
            // Capturado enquanto a transação produtora ainda está aberta.
            org.mockito.Mockito.verify(propagador).capturar();
        });
        assertThat(jdbc.queryForObject("SELECT jsonb_exists(envelope, '_carrierRastreio') FROM outbox WHERE message_id=?",
                Boolean.class, resp.messageId())).isTrue();
        var corpos = new ArrayList<String>();
        var carriers = new ArrayList<Map<String, String>>();
        var p = publisher((osId, messageId, corpo, traco) -> { corpos.add(corpo); carriers.add(traco); return "ack"; });
        assertThat(p.publicarProximo()).isTrue();
        assertThat(carriers).containsExactly(carrier);
        assertThat(corpos.get(0)).doesNotContain("_carrierRastreio").doesNotContain("tracestate");
    }

    @Test void semCarrierOEnvioSegueComCarrierVazio() {
        org.mockito.Mockito.when(propagador.capturar()).thenReturn(Map.of());
        transaction.executeWithoutResult(st -> outbox.registrar(resposta(UUID.randomUUID(), "OrcamentoGerado")));
        var carriers = new ArrayList<Map<String, String>>();
        assertThat(publisher((o, m, corpo, traco) -> { carriers.add(traco); return "ack"; }).publicarProximo()).isTrue();
        assertThat(carriers).containsExactly(Map.of());
    }
}
