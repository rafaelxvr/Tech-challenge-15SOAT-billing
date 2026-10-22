package com.oficina.billing.application;

import com.oficina.billing.application.port.GatewayIndisponivelException;

import java.time.Duration;
import java.util.function.LongConsumer;

/** Bounded, linear retry for provider calls only. */
public class TentativasGateway {
    private final int maximo;
    private final Duration esperaBase;
    private final LongConsumer dormir;

    public TentativasGateway(int maximo, Duration esperaBase, LongConsumer dormir) {
        if (maximo < 1) throw new IllegalArgumentException("Ao menos uma tentativa");
        this.maximo = maximo;
        this.esperaBase = esperaBase;
        this.dormir = dormir;
    }

    public void executar(Runnable acao) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                acao.run();
                return;
            } catch (GatewayIndisponivelException e) {
                if (tentativa >= maximo) throw e;
                dormir.accept(esperaBase.multipliedBy(tentativa).toMillis());
            }
        }
    }
}
