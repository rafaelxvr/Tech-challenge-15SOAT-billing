package com.oficina.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinheiroTest {
    @Test void arredondaMeioParaParEmDuasCasas() {
        assertThat(Dinheiro.de("35.555").valor()).isEqualByComparingTo("35.56");
        assertThat(Dinheiro.de("35.545").valor()).isEqualByComparingTo("35.54");
        assertThat(Dinheiro.de("10").valor().scale()).isEqualTo(2);
    }

    @Test void somaEMultiplica() {
        assertThat(Dinheiro.de("10.10").somar(Dinheiro.de("0.05")).valor()).isEqualByComparingTo("10.15");
        assertThat(Dinheiro.de("33.33").multiplicar(3).valor()).isEqualByComparingTo("99.99");
    }

    @Test void negativoEQuantidadeInvalidaSaoRejeitados() {
        assertThatThrownBy(() -> new Dinheiro(new BigDecimal("-0.01"))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Dinheiro(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Dinheiro.de("1.00").multiplicar(0)).isInstanceOf(RegraNegocioException.class);
    }

    @Test void igualdadePorValorNormalizado() {
        assertThat(Dinheiro.de("1.5")).isEqualTo(Dinheiro.de("1.50"));
    }
}
