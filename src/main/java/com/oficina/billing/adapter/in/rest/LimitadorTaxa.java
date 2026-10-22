package com.oficina.billing.adapter.in.rest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-window request counter per origin; coarse flood protection for the unauthenticated webhook endpoint.
 * An origin that floods exhausts only its own window, so it cannot starve the provider's legitimate notifications.
 */
public class LimitadorTaxa {
    static final int MAXIMO_ORIGENS = 10_000;
    private static final String ORIGEM_AGREGADA = "*";
    /** One slot stays reserved for the aggregate window, so the table never exceeds MAXIMO_ORIGENS. */
    private static final int LIMITE_UTIL = MAXIMO_ORIGENS - 1;

    private final int maximoPorJanela;
    private final Duration janela;
    private final Clock clock;
    private final Map<String, Contador> contadores = new HashMap<>();

    public LimitadorTaxa(int maximoPorJanela, Duration janela, Clock clock) {
        this.maximoPorJanela = maximoPorJanela;
        this.janela = janela;
        this.clock = clock;
    }

    public synchronized boolean permitir(String origem) {
        Instant agora = clock.instant();
        Contador contador = contadores.get(origem);
        if (contador == null) {
            if (contadores.size() >= LIMITE_UTIL) {
                contadores.values().removeIf(c -> c.expirou(agora, janela));
            }
            // Memory stays bounded: once the table is full of live origins, newcomers share one aggregate window.
            String chave = contadores.size() >= LIMITE_UTIL ? ORIGEM_AGREGADA : origem;
            contador = contadores.computeIfAbsent(chave, k -> new Contador(agora));
        }
        if (contador.expirou(agora, janela)) {
            contador.reiniciar(agora);
        }
        if (contador.usados >= maximoPorJanela) return false;
        contador.usados++;
        return true;
    }

    synchronized int origensRastreadas() { return contadores.size(); }

    private static final class Contador {
        private Instant inicio;
        private int usados;

        Contador(Instant inicio) { this.inicio = inicio; }

        boolean expirou(Instant agora, Duration janela) { return !agora.isBefore(inicio.plus(janela)); }

        void reiniciar(Instant agora) { inicio = agora; usados = 0; }
    }
}
