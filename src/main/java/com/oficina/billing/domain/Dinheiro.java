package com.oficina.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** BRL amount: scale 2, HALF_EVEN, never negative. */
public record Dinheiro(BigDecimal valor) {
    public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);

    public Dinheiro {
        Objects.requireNonNull(valor, "valor");
        if (valor.signum() < 0) throw new RegraNegocioException("Valor monetário não pode ser negativo");
        valor = valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static Dinheiro de(String valor) { return new Dinheiro(new BigDecimal(valor)); }

    public Dinheiro somar(Dinheiro outro) { return new Dinheiro(valor.add(outro.valor)); }

    public Dinheiro multiplicar(int quantidade) {
        if (quantidade < 1) throw new RegraNegocioException("Quantidade deve ser positiva");
        return new Dinheiro(valor.multiply(BigDecimal.valueOf(quantidade)));
    }
}
