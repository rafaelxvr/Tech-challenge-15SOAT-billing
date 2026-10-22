package com.oficina.billing.application.port;

import java.util.UUID;

/** One bounded FIFO send to the saga reply queue; returns the broker acknowledgement id. */
@FunctionalInterface
public interface EnviadorMensagens {
    String enviar(UUID osId, UUID messageId, String corpo);
}
