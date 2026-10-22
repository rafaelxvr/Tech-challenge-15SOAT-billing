package com.oficina.billing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Priced quote for one service-order round (osId + versao). */
@Entity
@Table(name = "orcamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orcamento {
    @Id private UUID id;
    @Column(name = "os_id", nullable = false, updatable = false) private UUID osId;
    @Column(name = "saga_id", nullable = false, updatable = false) private UUID sagaId;
    @Column(name = "cliente_id", nullable = false, updatable = false) private UUID clienteId;
    @Column(name = "numero_os", nullable = false, updatable = false) private long numeroOs;
    @Column(name = "versao", nullable = false, updatable = false) private int versao;

    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "orcamento_item", joinColumns = @JoinColumn(name = "orcamento_id"))
    @OrderColumn(name = "ordem")
    private List<ItemOrcamento> itens = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoOrcamento estado;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;
    @Version @Column(name = "versao_registro", nullable = false) private Long versaoRegistro;

    public static Orcamento gerar(UUID osId, UUID sagaId, UUID clienteId, long numeroOs, int versao,
                                  List<ItemOrcamento> itens, Instant agora) {
        if (osId == null || sagaId == null || clienteId == null || itens == null || agora == null) {
            throw new RegraNegocioException("Orçamento incompleto");
        }
        if (numeroOs < 1 || versao < 1) throw new RegraNegocioException("Número da OS e versão devem ser positivos");
        Orcamento o = new Orcamento();
        o.id = UUID.randomUUID();
        o.osId = osId;
        o.sagaId = sagaId;
        o.clienteId = clienteId;
        o.numeroOs = numeroOs;
        o.versao = versao;
        o.itens = new ArrayList<>(itens);
        o.total = itens.stream().map(ItemOrcamento::subtotal).reduce(Dinheiro.ZERO, Dinheiro::somar).valor();
        o.estado = EstadoOrcamento.GERADO;
        o.criadoEm = agora;
        o.atualizadoEm = agora;
        return o;
    }

    public List<ItemOrcamento> getItens() { return List.copyOf(itens); }

    public Dinheiro valorTotal() { return new Dinheiro(total); }

    public void aprovar(Instant agora) {
        exigir(EstadoOrcamento.GERADO);
        estado = EstadoOrcamento.APROVADO;
        atualizadoEm = Objects.requireNonNull(agora);
    }

    public void recusar(Instant agora) {
        exigir(EstadoOrcamento.GERADO);
        estado = EstadoOrcamento.RECUSADO;
        atualizadoEm = Objects.requireNonNull(agora);
    }

    /** Idempotent; a refused quote is already final and stays RECUSADO. Returns true when the state changed. */
    public boolean cancelar(Instant agora) {
        if (estado == EstadoOrcamento.CANCELADO || estado == EstadoOrcamento.RECUSADO) return false;
        estado = EstadoOrcamento.CANCELADO;
        atualizadoEm = Objects.requireNonNull(agora);
        return true;
    }

    private void exigir(EstadoOrcamento esperado) {
        if (estado != esperado) {
            throw new RegraNegocioException("Orçamento %s em %s; esperado %s".formatted(id, estado, esperado));
        }
    }
}
