package com.oficina.billing.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemOrcamentoTest {
    @Test void subtotalMultiplicaPrecoPelaQuantidade() {
        var item = new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Filtro", 2, Dinheiro.de("35.50"));
        assertThat(item.subtotal()).isEqualTo(Dinheiro.de("71.00"));
        assertThat(item.getPrecoUnitario()).isEqualByComparingTo("35.50");
    }

    @Test void validaCampos() {
        UUID ref = UUID.randomUUID();
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, ref, "Filtro", 0, Dinheiro.de("1")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, ref, " ", 1, Dinheiro.de("1")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(null, ref, "Filtro", 1, Dinheiro.de("1")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.SERVICO, null, "Filtro", 1, Dinheiro.de("1")))
                .isInstanceOf(RegraNegocioException.class);
    }
}
