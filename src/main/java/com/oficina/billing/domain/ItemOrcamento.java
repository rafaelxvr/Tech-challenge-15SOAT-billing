package com.oficina.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Price snapshot of one service or part line; billing never reads os-service's catalog. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemOrcamento {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private TipoItem tipo;
    @Column(name = "referencia_id", nullable = false) private UUID referenciaId;
    @Column(nullable = false, length = 255) private String descricao;
    @Column(nullable = false) private int quantidade;
    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2) private BigDecimal precoUnitario;

    public ItemOrcamento(TipoItem tipo, UUID referenciaId, String descricao, int quantidade, Dinheiro precoUnitario) {
        if (tipo == null || referenciaId == null || precoUnitario == null) {
            throw new RegraNegocioException("Item do orçamento incompleto");
        }
        if (descricao == null || descricao.isBlank()) throw new RegraNegocioException("Descrição obrigatória");
        if (quantidade < 1) throw new RegraNegocioException("Quantidade deve ser positiva");
        this.tipo = tipo;
        this.referenciaId = referenciaId;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario.valor();
    }

    public Dinheiro subtotal() { return new Dinheiro(precoUnitario).multiplicar(quantidade); }
}
