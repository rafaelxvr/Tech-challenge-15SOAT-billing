package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.support.PostgresTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcDedupWebhook.class)
class JdbcDedupWebhookTest extends PostgresTeste {
    @Autowired JdbcDedupWebhook dedup;

    @Test void entregaMarcadaPassaAConstarComoProcessadaSomentePeloMesmoPar() {
        assertThat(dedup.jaProcessado("req-a", "pay-1")).isFalse();
        dedup.marcarProcessado("req-a", "pay-1");
        dedup.marcarProcessado("req-a", "pay-1");
        assertThat(dedup.jaProcessado("req-a", "pay-1")).isTrue();
        assertThat(dedup.jaProcessado("req-a", "pay-2")).isFalse();
        assertThat(dedup.jaProcessado("req-b", "pay-1")).isFalse();
    }

    @Test void registrosAntigosSaoPurgadosAoMarcarNovaEntrega() {
        dedup.marcarProcessado("req-velho", "pay-9");
        jdbc.update("UPDATE webhook_evento SET recebido_em = CURRENT_TIMESTAMP - INTERVAL '2 days' WHERE request_id='req-velho'");
        dedup.marcarProcessado("req-novo", "pay-9");
        assertThat(dedup.jaProcessado("req-velho", "pay-9")).isFalse();
        assertThat(dedup.jaProcessado("req-novo", "pay-9")).isTrue();
    }
}
