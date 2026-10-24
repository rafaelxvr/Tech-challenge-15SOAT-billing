package com.oficina.billing.application;

import java.time.Duration;
import java.util.Objects;

public record PoliticaPagamento(Duration validade) {
    public PoliticaPagamento {
        Objects.requireNonNull(validade, "validade");
        if (validade.isNegative() || validade.isZero()) throw new IllegalArgumentException("Validade deve ser positiva");
    }
}
