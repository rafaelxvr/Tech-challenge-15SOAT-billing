package com.oficina.billing.adapter.out.persistence;

import com.oficina.billing.support.PostgresTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcInbox.class)
class JdbcInboxTest extends PostgresTeste {
    @Autowired JdbcInbox inbox;

    @Test void segundaEntregaDoMesmoIdNaoERegistrada() {
        UUID id = UUID.randomUUID();
        assertThat(Boolean.TRUE.equals(transaction.execute(st -> inbox.registrar(id, "GerarOrcamento")))).isTrue();
        assertThat(!Boolean.TRUE.equals(transaction.execute(st -> inbox.registrar(id, "GerarOrcamento")))).isTrue();
    }
}
