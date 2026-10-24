package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.application.port.DedupWebhookPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcDedupWebhook implements DedupWebhookPort {
    private static final String RETENCAO = "1 day";
    private final JdbcTemplate jdbc;

    @Override
    public boolean jaProcessado(String requestId, String dataId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM webhook_evento WHERE request_id=? AND data_id=?",
                Integer.class, requestId, dataId);
        return total != null && total > 0;
    }

    @Override
    public void marcarProcessado(String requestId, String dataId) {
        jdbc.update("INSERT INTO webhook_evento(request_id, data_id) VALUES (?,?) ON CONFLICT DO NOTHING", requestId, dataId);
        jdbc.update("DELETE FROM webhook_evento WHERE recebido_em < CURRENT_TIMESTAMP - INTERVAL '" + RETENCAO + "'");
    }
}
