package com.oficina.billing.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcamentoTest {
    static final Instant T0 = Instant.parse("2026-09-15T12:00:00Z");

    static Orcamento gerado() {
        return Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 42, 1, List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de óleo", 1, Dinheiro.de("80.00")),
                new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Filtro", 2, Dinheiro.de("35.50"))), T0);
    }

    @Test void totalSomaSubtotaisArredondados() {
        var o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, List.of(
                new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Parafuso", 3, Dinheiro.de("0.335")),
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Mão de obra", 1, Dinheiro.de("99.995"))), T0);
        assertThat(o.valorTotal()).isEqualTo(Dinheiro.de("101.02"));
        assertThat(o.getEstado()).isEqualTo(EstadoOrcamento.GERADO);
    }

    @Test void gerarSemItensTemTotalZero() {
        var o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, List.of(), T0);
        assertThat(o.valorTotal()).isEqualTo(Dinheiro.ZERO);
    }

    @Test void gerarValidaReferencias() {
        assertThatThrownBy(() -> Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 1, List.of(), T0))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 0, List.of(), T0))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, null, T0))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test void aprovarERecusarSoAPartirDeGerado() {
        var aprovado = gerado();
        aprovado.aprovar(T0);
        assertThat(aprovado.getEstado()).isEqualTo(EstadoOrcamento.APROVADO);
        assertThatThrownBy(() -> aprovado.recusar(T0)).isInstanceOf(RegraNegocioException.class);
        var recusado = gerado();
        recusado.recusar(T0);
        assertThat(recusado.getEstado()).isEqualTo(EstadoOrcamento.RECUSADO);
        assertThatThrownBy(() -> recusado.aprovar(T0)).isInstanceOf(RegraNegocioException.class);
    }

    @Test void cancelarEIdempotenteENaoMexeEmRecusado() {
        var o = gerado();
        o.aprovar(T0);
        assertThat(o.cancelar(T0)).isTrue();
        assertThat(o.getEstado()).isEqualTo(EstadoOrcamento.CANCELADO);
        assertThat(o.cancelar(T0)).isFalse();
        var recusado = gerado();
        recusado.recusar(T0);
        assertThat(recusado.cancelar(T0)).isFalse();
        assertThat(recusado.getEstado()).isEqualTo(EstadoOrcamento.RECUSADO);
    }

    @Test void itensSaoImutaveisParaQuemLe() {
        var o = gerado();
        assertThatThrownBy(() -> o.getItens().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(o.getItens()).hasSize(2);
    }
}
