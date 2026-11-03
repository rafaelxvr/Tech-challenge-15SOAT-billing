package com.oficina.billing.mensagem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Structural validation only; domain validity of lines is decided by Orcamento (→ OrcamentoFalhou). */
public record GerarOrcamentoPayload(long numero, UUID clienteId, int versaoOrcamento, List<ItemPayload> itens) {
    public record ItemPayload(String tipo, UUID referenciaId, String descricao, int quantidade, BigDecimal precoUnitario) {}

    public GerarOrcamentoPayload {
        if (numero < 1 || versaoOrcamento < 1 || clienteId == null || itens == null) {
            throw new IllegalArgumentException("GerarOrcamento incompleto");
        }
        itens = List.copyOf(itens);
    }
}
