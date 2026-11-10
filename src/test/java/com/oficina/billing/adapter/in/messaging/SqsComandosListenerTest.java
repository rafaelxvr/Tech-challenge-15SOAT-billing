package com.oficina.billing.adapter.in.messaging;

import com.oficina.billing.application.ProcessadorComandos;
import com.oficina.billing.application.rastreio.PropagadorTraco;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class SqsComandosListenerTest {
    final SqsClient sqs = mock(SqsClient.class);
    final ProcessadorComandos processador = mock(ProcessadorComandos.class);
    final PropagadorTraco propagador = mock(PropagadorTraco.class);
    final SqsComandosListener listener = new SqsComandosListener(sqs, "https://sqs.local/000/billing-commands.fifo", processador, propagador);

    static Message mensagem(String corpo, String grupo, String recibo) {
        return Message.builder().body(corpo).receiptHandle(recibo)
                .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, grupo)).build();
    }

    @Test void processaEApagaCadaMensagem() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(mensagem("a", "os-1", "r1"), mensagem("b", "os-2", "r2")).build());
        assertThat(listener.receberUmLote()).isEqualTo(2);
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test void falhaNaoDeixaMensagemPosteriorDoMesmoGrupoPassar() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(mensagem("falha", "os-1", "r1"), mensagem("depois", "os-1", "r2"), mensagem("outra", "os-2", "r3")).build());
        doThrow(new IllegalStateException("x")).when(processador).processar("falha");
        assertThat(listener.receberUmLote()).isEqualTo(1);
        verify(processador, never()).processar("depois");
        verify(processador).processar("outra");
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test void falhaNaRecepcaoDoLoteNaoDerrubaOLoopDeConsumo() throws Exception {
        var segundaChamada = new java.util.concurrent.CountDownLatch(1);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new IllegalStateException("fila indisponível"))
                .thenAnswer(inv -> {
                    segundaChamada.countDown();
                    return ReceiveMessageResponse.builder().build();
                });
        listener.start();
        try {
            assertThat(segundaChamada.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            listener.stop();
        }
        assertThat(listener.isRunning()).isFalse();
    }

    @Test void pedeAtributoDeGrupoELongPolling() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().build());
        listener.receberUmLote();
        verify(sqs).receiveMessage(argThat((ReceiveMessageRequest r) -> r.waitTimeSeconds() == 10
                && r.maxNumberOfMessages() == 10
                && r.messageSystemAttributeNames().contains(MessageSystemAttributeName.MESSAGE_GROUP_ID)));
    }

    @Test void pedeOsAtributosDeRastreioDaMensagem() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().build());
        listener.receberUmLote();
        verify(sqs).receiveMessage(argThat((ReceiveMessageRequest r) ->
                r.messageAttributeNames().containsAll(java.util.List.of("traceparent", "tracestate", "newrelic"))));
    }

    @Test void aceitaOsHeadersDeRastreioAntesDaLogicaDeNegocio() {
        var traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        var msg = Message.builder().body("a").receiptHandle("r1")
                .attributes(Map.of(MessageSystemAttributeName.MESSAGE_GROUP_ID, "os-1"))
                .messageAttributes(Map.of("traceparent", MessageAttributeValue.builder().dataType("String").stringValue(traceparent).build()))
                .build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(msg).build());
        listener.receberUmLote();
        var ordem = inOrder(propagador, processador);
        ordem.verify(propagador).aceitar(Map.of("traceparent", traceparent));
        ordem.verify(processador).processar("a");
    }

    @Test void processarNaTransacaoAbreTransacaoDispatcher() throws NoSuchMethodException {
        var trace = SqsComandosListener.class.getDeclaredMethod("processarNaTransacao", Message.class)
                .getAnnotation(com.newrelic.api.agent.Trace.class);
        assertThat(trace).isNotNull();
        assertThat(trace.dispatcher()).isTrue();
    }
}
