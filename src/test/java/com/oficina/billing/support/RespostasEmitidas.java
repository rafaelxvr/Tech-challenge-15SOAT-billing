package com.oficina.billing.support;

import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.MapeadorMensagens;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

public final class RespostasEmitidas {
    private RespostasEmitidas() {}

    public static List<Envelope> de(JdbcTemplate jdbc, MapeadorMensagens mapeador, UUID osId) {
        return jdbc.queryForList("SELECT envelope::text FROM outbox WHERE os_id=? ORDER BY sequencia", String.class, osId)
                .stream().map(mapeador::ler).toList();
    }
}
