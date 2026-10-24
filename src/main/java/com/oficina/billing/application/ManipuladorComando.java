package com.oficina.billing.application;

import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.TipoComando;

/** Strategy: one implementation per saga command; registered by type in RoteadorComandos. */
public interface ManipuladorComando {
    TipoComando tipo();

    void executar(Envelope comando);
}
