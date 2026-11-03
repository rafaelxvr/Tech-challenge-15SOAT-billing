package com.oficina.billing.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrcamentoResponse(UUID id, UUID osId, int versao, String estado, BigDecimal total, Instant criadoEm,
                                List<Item> itens) {
    public record Item(String tipo, UUID referenciaId, String descricao, int quantidade, BigDecimal precoUnitario) {}
}
