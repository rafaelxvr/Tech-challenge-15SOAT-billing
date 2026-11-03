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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({JdbcOutbox.class, MapeadorMensagens.class})
class OutboxPublisherTest extends PostgresTeste {
    // O outbox usa CURRENT_TIMESTAMP do banco como disponivel_em; o relógio do teste precisa estar à frente dele.
    static final Instant T = Instant.now().plus(java.time.Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
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
        var p = publisher((osId, messageId, corpo) -> { enviados.add(messageId); return "sqs-" + messageId; });
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
        var p = publisher((osId, messageId, corpo) -> { throw new IllegalStateException("token=secret"); });
        assertThat(p.publicarProximo()).isTrue();
        assertThat(jdbc.queryForMap("SELECT estado, tentativas, ultimo_erro_codigo FROM outbox WHERE message_id=?",
                primeira.messageId())).containsEntry("estado", "PENDING").containsEntry("tentativas", 1)
                .containsEntry("ultimo_erro_codigo", "QUEUE_SEND_FAILED");
        assertThat(p.publicarProximo()).isFalse();
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
}
