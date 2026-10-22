package com.oficina.billing.adapter.in.rest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Fixed-window request counter; coarse flood protection for the unauthenticated webhook endpoint. */
public class LimitadorTaxa {
    private final int maximoPorJanela;
    private final Duration janela;
    private final Clock clock;
    private Instant inicioJanela;
    private int usados;

    public LimitadorTaxa(int maximoPorJanela, Duration janela, Clock clock) {
        this.maximoPorJanela = maximoPorJanela;
        this.janela = janela;
        this.clock = clock;
    }

    public synchronized boolean permitir() {
        Instant agora = clock.instant();
        if (inicioJanela == null || !agora.isBefore(inicioJanela.plus(janela))) {
            inicioJanela = agora;
            usados = 0;
        }
        if (usados >= maximoPorJanela) return false;
        usados++;
        return true;
    }
}
