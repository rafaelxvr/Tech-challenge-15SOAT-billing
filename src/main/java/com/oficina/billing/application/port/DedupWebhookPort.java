package com.oficina.billing.application.port;

/** Remembers verified webhook deliveries so a replay of the same notification changes nothing. */
public interface DedupWebhookPort {
    boolean jaProcessado(String requestId, String dataId);

    void marcarProcessado(String requestId, String dataId);
}
