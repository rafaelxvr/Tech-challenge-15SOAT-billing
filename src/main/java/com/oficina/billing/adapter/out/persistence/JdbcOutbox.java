package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.application.port.SaidaMensagensPort;
import com.oficina.billing.mensagem.Envelope;
import com.oficina.billing.mensagem.MapeadorMensagens;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JdbcOutbox implements SaidaMensagensPort {
    private final JdbcTemplate jdbc;
    private final MapeadorMensagens mapeador;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Envelope envelope) {
        String corpo = mapeador.escrever(envelope);
        if (corpo.getBytes(StandardCharsets.UTF_8).length > Envelope.MAX_BYTES) {
            throw new IllegalArgumentException("Envelope acima de 64 KB");
        }
        jdbc.update("INSERT INTO outbox(message_id, os_id, tipo, envelope) VALUES (?,?,?,?::jsonb)",
                envelope.messageId(), envelope.osId(), envelope.type(), corpo);
    }
}
