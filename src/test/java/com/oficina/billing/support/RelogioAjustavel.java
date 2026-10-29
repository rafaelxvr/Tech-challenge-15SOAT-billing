package com.oficina.billing.support;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public class RelogioAjustavel extends Clock {
    private final AtomicReference<Instant> agora;

    public RelogioAjustavel(Instant inicio) { this.agora = new AtomicReference<>(inicio); }

    public void definir(Instant instante) { agora.set(instante); }

    public void avancar(Duration duracao) { agora.updateAndGet(i -> i.plus(duracao)); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return agora.get(); }
}
