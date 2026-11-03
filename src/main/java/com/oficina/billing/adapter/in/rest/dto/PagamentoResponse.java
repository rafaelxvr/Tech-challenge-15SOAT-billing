package com.oficina.billing.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PagamentoResponse(UUID id, UUID orcamentoId, UUID osId, String estado, BigDecimal valor,
                                String checkoutUrl, Instant expiraEm) {}
