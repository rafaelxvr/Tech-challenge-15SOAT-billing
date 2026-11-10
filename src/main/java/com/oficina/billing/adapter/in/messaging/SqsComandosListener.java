package com.oficina.billing.adapter.in.messaging;

import com.newrelic.api.agent.Trace;
import com.oficina.billing.application.ProcessadorComandos;
import com.oficina.billing.application.rastreio.CarrierRastreio;
import com.oficina.billing.application.rastreio.PropagadorTraco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Long-polls the command queue; a failed message holds back later messages of its FIFO group. */
public class SqsComandosListener implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(SqsComandosListener.class);
    private final SqsClient sqs;
    private final String fila;
    private final ProcessadorComandos processador;
    private final PropagadorTraco propagador;
    private ExecutorService executor;
    private volatile boolean rodando;

    public SqsComandosListener(SqsClient sqs, String fila, ProcessadorComandos processador,
                              PropagadorTraco propagador) {
        this.sqs = sqs;
        this.fila = fila;
        this.processador = processador;
        this.propagador = propagador;
    }

    public int receberUmLote() {
        var mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(fila).maxNumberOfMessages(10)
                .waitTimeSeconds(10).messageSystemAttributeNames(MessageSystemAttributeName.MESSAGE_GROUP_ID)
                .messageAttributeNames(CarrierRastreio.NOMES).build()).messages();
        Set<String> gruposComFalha = new HashSet<>();
        int processadas = 0;
        for (Message mensagem : mensagens) {
            String grupo = mensagem.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
            if (grupo != null && gruposComFalha.contains(grupo)) continue;
            try {
                processarNaTransacao(mensagem);
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(fila).receiptHandle(mensagem.receiptHandle()).build());
                processadas++;
            } catch (RuntimeException e) {
                if (grupo != null) gruposComFalha.add(grupo);
                MDC.put("event_name", "billing_comando_falhou");
                MDC.put("error_code", e.getClass().getSimpleName());
                try { LOG.warn("Falha ao processar comando recebido da fila de billing"); } finally { MDC.remove("event_name"); MDC.remove("error_code"); }
            }
        }
        return processadas;
    }

    /** Uma transação New Relic por mensagem, ligada ao trace do produtor antes de qualquer lógica de negócio. */
    @Trace(dispatcher = true)
    void processarNaTransacao(Message mensagem) {
        propagador.aceitar(carrier(mensagem));
        processador.processar(mensagem.body());
    }

    private static Map<String, String> carrier(Message mensagem) {
        Map<String, String> headers = new LinkedHashMap<>();
        mensagem.messageAttributes().forEach((nome, atributo) -> {
            if (atributo != null && atributo.stringValue() != null) headers.put(nome, atributo.stringValue());
        });
        return headers;
    }

    @Override public synchronized void start() {
        if (isRunning()) return;
        rodando = true;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "billing-comandos");
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            while (rodando) {
                try {
                    receberUmLote();
                } catch (RuntimeException e) {
                    MDC.put("event_name", "billing_recepcao_falhou");
                    try { LOG.warn("Falha ao receber lote de comandos da fila de billing"); } finally { MDC.remove("event_name"); }
                    dormir();
                }
            }
        });
    }

    private static void dormir() {
        try { Thread.sleep(1_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override public synchronized void stop() {
        rodando = false;
        if (executor != null) executor.shutdownNow();
    }

    @Override public synchronized boolean isRunning() { return executor != null && !executor.isShutdown(); }
}
