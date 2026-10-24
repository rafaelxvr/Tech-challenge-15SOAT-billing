package com.oficina.billing.application;

import com.oficina.billing.application.port.InboxPort;
import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.MapeadorMensagens;
import com.oficina.billing.mensagem.MensagemInvalidaException;
import com.oficina.billing.mensagem.TipoComando;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** One command = one transaction: inbox row, aggregate changes and reply commit together. */
@Component
@RequiredArgsConstructor
public class ProcessadorComandos {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessadorComandos.class);
    private final MapeadorMensagens mapeador;
    private final InboxPort inbox;
    private final RoteadorComandos roteador;

    @Transactional
    public void processar(String corpo) {
        Envelope comando = mapeador.ler(corpo);
        if (comando.kind() != Envelope.Kind.COMMAND) throw new MensagemInvalidaException("Somente comandos são aceitos");
        String priorCorrelation = MDC.get("correlation_id");
        String priorTrace = MDC.get("traceparent");
        try {
            MDC.put("correlation_id", comando.correlationId());
            if (comando.traceparent() != null) MDC.put("traceparent", comando.traceparent());
            MDC.put("saga_id", comando.sagaId().toString());
            MDC.put("os_id", comando.osId().toString());
            if (!inbox.registrar(comando.messageId(), comando.type())) {
                registrar("billing_comando_duplicado");
                return;
            }
            Optional<TipoComando> tipo = TipoComando.deTipo(comando.type());
            if (tipo.isEmpty() || !roteador.suporta(tipo.get())) {
                registrar("billing_comando_desconhecido");
                return;
            }
            roteador.aplicar(tipo.get(), comando);
        } finally {
            MDC.remove("saga_id");
            MDC.remove("os_id");
            restaurar("correlation_id", priorCorrelation);
            restaurar("traceparent", priorTrace);
        }
    }

    private static void registrar(String evento) {
        MDC.put("event_name", evento);
        try { LOG.info(""); } finally { MDC.remove("event_name"); }
    }

    private static void restaurar(String chave, String valor) {
        if (valor == null) MDC.remove(chave); else MDC.put(chave, valor);
    }
}
