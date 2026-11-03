package com.oficina.billing.application.port;

import com.oficina.billing.mensagem.Envelope;

/** Records an outgoing reply in the caller's transaction; delivery happens after commit. */
public interface SaidaMensagensPort {
    void registrar(Envelope envelope);
}
