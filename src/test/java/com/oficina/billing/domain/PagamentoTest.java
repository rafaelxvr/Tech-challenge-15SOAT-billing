package com.oficina.billing.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoTest {
    static final Instant T0 = Instant.parse("2026-09-15T12:00:00Z");
    static final Instant EXPIRA = T0.plus(Duration.ofMinutes(30));
    static final ReferenciaComando ORIGEM = new ReferenciaComando(UUID.randomUUID(), UUID.randomUUID(), 3,
            UUID.randomUUID(), UUID.randomUUID().toString(), null);

    static Pagamento pendente() {
        var o = OrcamentoTest.gerado();
        o.aprovar(T0);
        return Pagamento.abrir(UUID.randomUUID(), o, "pref-1", "https://mp.example/checkout/1", EXPIRA, ORIGEM, T0);
    }

    @Test void abrirExigeOrcamentoAprovadoUrlSeguraEValidadeFutura() {
        var gerado = OrcamentoTest.gerado();
        assertThatThrownBy(() -> Pagamento.abrir(UUID.randomUUID(), gerado, "p", "https://x", EXPIRA, ORIGEM, T0))
                .isInstanceOf(RegraNegocioException.class);
        var aprovado = OrcamentoTest.gerado();
        aprovado.aprovar(T0);
        assertThatThrownBy(() -> Pagamento.abrir(UUID.randomUUID(), aprovado, "p", "http://x", EXPIRA, ORIGEM, T0))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Pagamento.abrir(UUID.randomUUID(), aprovado, "p", "https://x", T0, ORIGEM, T0))
                .isInstanceOf(RegraNegocioException.class);
        var p = Pagamento.abrir(UUID.randomUUID(), aprovado, "p", "https://x", EXPIRA, ORIGEM, T0);
        assertThat(p.getValor()).isEqualByComparingTo(aprovado.getTotal());
        assertThat(p.getOrigem().getComandoId()).isEqualTo(ORIGEM.getComandoId());
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.PENDENTE);
    }

    @Test void confirmarEEstornar() {
        var p = pendente();
        p.confirmar("mp-1", T0);
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.CONFIRMADO);
        assertThat(p.getMpPaymentId()).isEqualTo("mp-1");
        assertThat(p.estornar(T0)).isTrue();
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.ESTORNADO);
        assertThat(p.estornar(T0)).isFalse();
    }

    @Test void estornarExigeConfirmado() {
        var p = pendente();
        assertThatThrownBy(() -> p.estornar(T0)).isInstanceOf(RegraNegocioException.class);
    }

    @Test void recusarSoPendente() {
        var p = pendente();
        p.recusar("mp-2", T0);
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.RECUSADO);
        assertThatThrownBy(() -> p.confirmar("mp-2", T0)).isInstanceOf(RegraNegocioException.class);
    }

    @Test void expiraSomentePendenteDepoisDoPrazo() {
        var p = pendente();
        assertThat(p.expirar(EXPIRA.minusSeconds(1))).isFalse();
        assertThat(p.expirar(EXPIRA)).isTrue();
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.EXPIRADO);
        assertThat(p.expirar(EXPIRA.plusSeconds(60))).isFalse();
    }

    @Test void cancelarSomentePendente() {
        var p = pendente();
        assertThat(p.cancelar(T0)).isTrue();
        assertThat(p.getEstado()).isEqualTo(EstadoPagamento.CANCELADO);
        assertThat(p.cancelar(T0)).isFalse();
        assertThat(p.encerradoSemCobranca()).isTrue();
    }

    @Test void confirmacaoTardiaSoDepoisDeCanceladoOuExpirado() {
        var ativo = pendente();
        assertThatThrownBy(() -> ativo.estornarConfirmacaoTardia("mp-3", T0)).isInstanceOf(RegraNegocioException.class);
        var cancelado = pendente();
        cancelado.cancelar(T0);
        cancelado.estornarConfirmacaoTardia("mp-3", T0);
        assertThat(cancelado.getEstado()).isEqualTo(EstadoPagamento.ESTORNADO);
        assertThat(cancelado.getMpPaymentId()).isEqualTo("mp-3");
    }
}
