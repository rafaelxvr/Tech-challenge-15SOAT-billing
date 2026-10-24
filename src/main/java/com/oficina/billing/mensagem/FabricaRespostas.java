package com.oficina.billing.mensagem;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oficina.billing.domain.ReferenciaComando;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** Builds replies per Plan 0 §4.1: correlation copied from the answered command, causationId = its messageId. */
@Component
public class FabricaRespostas {
    private final Clock clock;

    public FabricaRespostas(Clock clock) { this.clock = clock; }

    public static ReferenciaComando referencia(Envelope comando) {
        return new ReferenciaComando(comando.sagaId(), comando.osId(), comando.passo(), comando.messageId(),
                comando.correlationId(), comando.traceparent());
    }

    public Envelope responder(ReferenciaComando origem, String tipo, ObjectNode payload) {
        return new Envelope(UUID.randomUUID(), Envelope.Kind.REPLY, tipo, 1, origem.getSagaId(), origem.getOsId(),
                origem.getPasso(), origem.getComandoId(), clock.instant(), origem.getCorrelationId(),
                origem.getTraceparent(), payload);
    }
}
