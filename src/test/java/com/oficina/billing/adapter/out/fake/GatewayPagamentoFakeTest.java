package com.oficina.billing.adapter.out.fake;

import com.oficina.billing.application.port.GatewayIndisponivelException;
import com.oficina.billing.application.port.GatewayPagamento.*;
import com.oficina.billing.domain.Dinheiro;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayPagamentoFakeTest {
    final GatewayPagamentoFake gateway = new GatewayPagamentoFake();

    @Test void cobrancaTemUrlHttpsPrevisivel() {
        UUID pagamento = UUID.randomUUID();
        var c = gateway.criarCobranca(new SolicitacaoCobranca(pagamento, UUID.randomUUID(), 42, Dinheiro.de("10"),
                Instant.parse("2026-09-15T12:30:00Z")));
        assertThat(c.checkoutUrl()).isEqualTo("https://pagamento.fake.local/checkout/" + pagamento);
        assertThat(gateway.cobrancasCriadas()).isEqualTo(1);
    }

    @Test void simulacaoAlimentaConsultaEEstorno() {
        UUID pagamento = UUID.randomUUID();
        String id = gateway.simular(pagamento, StatusExterno.APROVADO);
        assertThat(gateway.consultarPagamento(id)).isEqualTo(new SituacaoPagamento(id, pagamento, StatusExterno.APROVADO, null, "BRL"));
        gateway.estornar(id, pagamento);
        assertThat(gateway.estornado(id)).isTrue();
        assertThatThrownBy(() -> gateway.consultarPagamento("desconhecido")).isInstanceOf(GatewayIndisponivelException.class);
    }

    @Test void estornoPodeFicarIndisponivel() {
        gateway.indisponibilizarEstornos(true);
        assertThatThrownBy(() -> gateway.estornar("x", UUID.randomUUID())).isInstanceOf(GatewayIndisponivelException.class)
                .extracting(e -> ((GatewayIndisponivelException) e).codigo()).isEqualTo("FAKE_ESTORNO_INDISPONIVEL");
    }
}
