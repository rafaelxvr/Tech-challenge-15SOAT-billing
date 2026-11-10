package com.oficina.billing.application;

import com.oficina.billing.application.port.InboxPort;
import com.oficina.billing.application.rastreio.PropagadorTraco;
import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.mensagem.TipoComando;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessadorComandosTracoTest {
    final MapeadorMensagens mapeador = mock(MapeadorMensagens.class);
    final InboxPort inbox = mock(InboxPort.class);
    final RoteadorComandos roteador = mock(RoteadorComandos.class);
    final PropagadorTraco propagador = mock(PropagadorTraco.class);
    final ProcessadorComandos processador = new ProcessadorComandos(mapeador, inbox, roteador, propagador);

    @Test void anotaSagaIdECorrelationIdSemPiiAntesDaLogicaDeNegocio() {
        UUID saga = UUID.randomUUID();
        String correlacao = UUID.randomUUID().toString();
        var comando = new Envelope(UUID.randomUUID(), Envelope.Kind.COMMAND, "GerarOrcamento", 1, saga, UUID.randomUUID(), 2,
                null, Instant.now(), correlacao, null,
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        when(mapeador.ler("corpo")).thenReturn(comando);
        when(inbox.registrar(any(), any())).thenReturn(true);
        when(roteador.suporta(any())).thenReturn(true);

        processador.processar("corpo");

        var ordem = inOrder(propagador, inbox, roteador);
        ordem.verify(propagador).anotar("sagaId", saga.toString());
        ordem.verify(propagador).anotar("correlationId", correlacao);
        ordem.verify(inbox).registrar(comando.messageId(), "GerarOrcamento");
        verify(roteador).aplicar(TipoComando.deTipo("GerarOrcamento").orElseThrow(), comando);
    }
}
